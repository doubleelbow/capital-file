(ns com.doubleelbow.capital.file.alpha
  (:require [clojure.core.async :refer [<!!]]
            [clojure.xml :as xml]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [com.doubleelbow.capital.alpha :as capital]
            [com.doubleelbow.capital.interceptor.alpha :as interceptor]
            [pathetic.core :as pathetic]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [io.pedestal.log :as log]))

(defprotocol FileSystem
  (exists [this path])
  (content [this path])
  (last-change-date [this path]))

(defrecord SimpleFileSystem []
  FileSystem
  (exists [this path]
    (.exists (io/file path)))
  (content [this path]
    (log/debug :msg "reading content" :path path)
    (slurp path))
  (last-change-date [this path]
    (c/from-long (.lastModified (io/file path)))))

(defn- absolute-path-intc [base-path]
  {::interceptor/name ::absolute-path
   ::interceptor/init {::base-path (pathetic/resolve "." base-path)}
   ::interceptor/up (fn [context]
                      (let [req (::capital/request context)
                            base-path (::base-path context)
                            full-path (pathetic/resolve base-path (::path req))]
                        (assoc-in context [::capital/request ::path] full-path)))})

(defn- file-path [context]
  (get-in context [::capital/request ::path]))

(defn- use-cache? [context]
  (get-in context [::capital/request ::use-cache?] (get-in context [::cache ::use-cache?] false)))

(defn- is-changed? [file-system file-path content-date]
  (if content-date
    (t/after? (last-change-date file-system file-path) content-date)
    true))

(defn- expired? [current-time obtained duration]
  (if obtained
    (t/after? current-time (t/plus obtained (t/seconds duration)))
    true))

(defn- invalidate-cache [context cache path]
  (let [duration (get-in context [::cache ::duration])
        check-if-newer? (get-in context [::cache ::check-if-newer?])]
    (cond
      (not (use-cache? context)) "not allowed to use cache"
      (nil? cache) "no cached value"
      (and check-if-newer? (is-changed? (::file-system context) path (::date cache))) "file has changed"
      (and (expired? (apply (::current-time context) [context]) (::obtained cache) duration)
           (or (not check-if-newer?) (is-changed? (::file-system context) path (::date cache)))) "cache has expired")))

(defn- cache-intc [cache-config]
  {::interceptor/name ::file-cache
   ::interceptor/init {::cache cache-config
                       ::cached-values (atom {})}
   ::interceptor/up (fn [context]
                      (let [path (file-path context) 
                            cache (get (deref (::cached-values context)) path)
                            reason (invalidate-cache context cache path)]
                        (if reason
                          (do
                            (log/debug :msg reason :path path :cache cache)
                            context)
                          (-> context
                              (assoc ::capital/response (::content cache))
                              (assoc ::capital/queue [])
                              (update ::capital/stack rest)))))
   ::interceptor/down (fn [context]
                        (if (and (use-cache? context) (not (contains? context :not-found)))
                          (let [path (file-path context)
                                c {::content (::capital/response context)
                                   ::obtained (apply (::current-time context) [context])
                                   ::date (last-change-date (::file-system context) path)}]
                            (do
                              (swap! (::cached-values context) assoc path c)
                              context))
                          context))})

(defn- get-extension-from-path [path]
  (let [file-name (.getName (io/file path))
        extension-point-index (.lastIndexOf file-name (int \.))]
    (if (not= -1 extension-point-index)
      (.substring file-name (inc extension-point-index))
      "txt")))

(defn- format-intc [format-config]
  {::interceptor/name ::format
   ::interceptor/init {::read-fmt (merge {"txt" #(identity %)
                                          "edn" #(clojure.edn/read-string %)
                                          "xml" #(-> %
                                                     .getBytes
                                                     java.io.ByteArrayInputStream.
                                                     xml/parse)}
                                         format-config)}
   ::interceptor/down (fn [context]
                        (let [extension (get-extension-from-path (file-path context))]
                          (update context ::capital/response (get (::read-fmt context) extension identity))))})

(defn- nonexistent-file-intc [val]
  {::interceptor/name ::nonexistent-file
   ::interceptor/init {::nonexistent val}
   ::interceptor/up (fn [context]
                      (let [nonexistent (get-in context [::capital/request ::nonexistent] (::nonexistent context ::throw-if-nonexistent))]
                        (if (or (= ::throw-if-nonexistent nonexistent) (exists (::file-system context) (file-path context)))
                          context
                          (-> context
                              (assoc ::capital/response nonexistent)
                              (assoc ::capital/queue [])
                              (assoc :not-found true)))))})

(def ^:private blocking-read-intc
  {::interceptor/name ::sync-read
   ::interceptor/up (fn [context]
                      (assoc context ::capital/response (content (::file-system context) (file-path context))))})


(defn initial-context [config]
  (let [interceptors [(absolute-path-intc (::base-path config))
                      (cache-intc (get-in config [::read-opts ::cache]))
                      (format-intc (get-in config [::read-opts ::format-config] {}))
                      (nonexistent-file-intc (get-in config [::read-opts ::nonexistent]))
                      blocking-read-intc]]
    (-> (capital/initial-context :capital-file interceptors)
        (assoc ::file-system (::file-system config (->SimpleFileSystem)))
        (assoc ::current-time (::current-time config #(t/now))))))

(defn read!
  ([path context]
   (read! path context {}))
  ([path context {nonexistent ::nonexistent use-cache ::use-cache?}]
   (log/debug :msg "executing read!" :path path)
   (let [request (cond-> {}
                   true (assoc ::path path)                 
                   (not (nil? nonexistent)) (assoc ::nonexistent nonexistent)
                   (not (nil? use-cache)) (assoc ::use-cache? use-cache))]
     (<!! (capital/<send! request context)))))
