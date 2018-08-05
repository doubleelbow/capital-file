(ns com.doubleelbow.capital.file.alpha
  (:require [clojure.core.async :refer [<!!]]
            [clojure.java.io :as io]
            [com.doubleelbow.capital.alpha :as capital]
            [com.doubleelbow.capital.interceptor.alpha :as interceptor]
            [pathetic.core :as pathetic]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [io.pedestal.log :as log]))

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

(defn- last-modified-date [file-path]
  (c/from-long (.lastModified (io/file file-path))))

(defn- is-changed? [file-path content-date]
  (if content-date
    (t/after? (last-modified-date file-path) content-date)
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
      (and check-if-newer? (is-changed? path (::date cache))) "file has changed"
      (expired? (t/now) (::obtained cache) duration) "cache has expired")))

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
                                   ::obtained (t/now)
                                   ::date (last-modified-date path)}]
                            (do
                              (swap! (::cached-values context) assoc path c)
                              context))
                          context))})

(defn- nonexistent-file-intc [val]
  {::interceptor/name ::nonexistent-file
   ::interceptor/init {::nonexistent val}
   ::interceptor/up (fn [context]
                      (let [nonexistent (get-in context [::capital/request ::nonexistent] (::nonexistent context ::throw-if-nonexistent))]
                        (if (or (= ::throw-if-nonexistent nonexistent) (.exists (io/file (get-in context [::capital/request ::path]))))
                          context
                          (-> context
                              (assoc ::capital/response nonexistent)
                              (assoc ::capital/queue [])
                              (assoc :not-found true)))))})

(def ^:private blocking-read-intc
  {::interceptor/name ::sync-read
   ::interceptor/up (fn [context]
                      (assoc context ::capital/response (slurp (get-in context [::capital/request ::path]))))})


(defn initial-context [config]
  (capital/initial-context :file :capital-file [(absolute-path-intc (::base-path config)) (cache-intc (get-in config [::read-opts ::cache])) (nonexistent-file-intc (get-in config [::read-opts ::nonexistent])) blocking-read-intc]))

(defn read!
  ([path context]
   (read! path context {}))
  ([path context {nonexistent ::nonexistent use-cache ::use-cache?}]
   (let [request (cond-> {}
                   true (assoc ::path path
                               ;;::use-cache? false
                               )                 
                   nonexistent (assoc ::nonexistent nonexistent)
                   ;;use-cache (assoc ::use-cache? use-cache)
                   )]
     (<!! (capital/<send! request context)))))
