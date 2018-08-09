(ns com.doubleelbow.capital.file
  (:require [com.doubleelbow.capital.file.alpha :as sut]
            [clojure.test :as t]
            [clj-time.core :as time]))

(defn- get-file [file-changes time]
  (reduce (fn [a b]
            (if (time/before? time (:change b))
              (reduced a)
              b))
          file-changes))

(defrecord FakeFileSystem [files times accesses]
  sut/FileSystem
  (exists [this path]
    (contains? files path))
  (content [this path]
    (swap! accesses conj {:file path :time (first @times)})
    (:content (get-file (get files path) (first @times))))
  (last-change-date [this path]
    (:change (get-file (get files path) (first @times)))))

(defn- initial-time []
  (time/today-at (rand-int 24) (rand-int 60) (rand-int 60)))

(defn- config
  ([duration]
   {::sut/read-opts {::sut/cache {::sut/use-cache? true
                                  ::sut/duration duration}}})
  ([duration check-if-newer?]
   (assoc-in (config duration) [::sut/read-opts ::sut/cache ::sut/check-if-newer?] check-if-newer?)))

(defn- gen-time-inside-duration [init-time duration]
  (time/plus init-time (time/seconds (rand-int duration))))

(defn- gen-time-outside-duration [init-time duration]
  (time/plus init-time (time/seconds (+ duration (rand-int (* 10 duration))))))

(defn- gen-time-before [init-time]
  (time/minus init-time (time/seconds (+ 1 (rand-int (* 24 60 60))))))

(defn- gen-time-on-interval [start-time end-time]
  (time/plus start-time (time/seconds (rand-int (time/in-seconds (time/interval start-time end-time))))))

(defn- initial-context [config files times]
  (let [config (-> config
                   (assoc ::sut/file-system (->FakeFileSystem files times (atom [])))
                   (assoc ::sut/current-time #(first (deref (:times %)))))]
    (-> (sut/initial-context config)
        (assoc :times times))))

(defn- read! [path context]
  (let [content (sut/read! path context)]
    (swap! (:times context) rest)
    content))

(t/deftest inside-duration-no-change
  (let [file-path "path/to/test/file.txt"
        duration (* 30 60)
        init-time (initial-time)
        next-time (gen-time-inside-duration init-time duration)
        read-times (atom [init-time next-time])
        init-content "content"
        init-change (gen-time-before init-time)
        init-fs {file-path [{:change init-change :content init-content}]}
        context (initial-context (config duration)
                                 init-fs
                                 read-times)
        expected-content [init-content init-content]
        actual-content [(read! file-path context) (read! file-path context)]
        accesses (get-in context [::sut/file-system :accesses])]
    (t/is (= expected-content actual-content))
    (t/is (= 1 (count @accesses)) "file system was accessed only once")))

(t/deftest inside-duration-no-change-check-if-newer
  (let [file-path "path/to/test/file.txt"
        duration (* 30 60)
        init-time (initial-time)
        next-time (gen-time-inside-duration init-time duration)
        read-times (atom [init-time next-time])
        init-content "content"
        init-change (gen-time-before init-time)
        init-fs {file-path [{:change init-change :content init-content}]}
        context (initial-context (config duration true)
                                 init-fs
                                 read-times)
        expected-content [init-content init-content]
        actual-content [(read! file-path context) (read! file-path context)]
        accesses (get-in context [::sut/file-system :accesses])]
    (t/is (= expected-content actual-content))
    (t/is (= 1 (count @accesses)) "file system was accessed only once")))

(t/deftest inside-duration-change
  (let [file-path "path/to/test/file.txt"
        duration (* 30 60)
        init-time (initial-time)
        next-time (gen-time-inside-duration init-time duration)
        read-times (atom [init-time next-time])
        init-content "content"
        next-content "changed content"
        init-change (gen-time-before init-time)
        next-change (gen-time-on-interval init-time next-time)
        init-fs {file-path [{:change init-change :content init-content}
                            {:change next-change :content next-content}]}
        context (initial-context (config duration)
                                 init-fs
                                 read-times)
        expected-content [init-content init-content]
        actual-content [(read! file-path context) (read! file-path context)]
        accesses (get-in context [::sut/file-system :accesses])]
    (t/is (= expected-content actual-content))
    (t/is (= 1 (count @accesses)) "file system was accessed only once")))

(t/deftest inside-duration-change-change-if-newer
  (let [file-path "path/to/test/file.txt"
        duration (* 30 60)
        init-time (initial-time)
        next-time (gen-time-inside-duration init-time duration)
        read-times (atom [init-time next-time])
        init-content "content"
        next-content "changed content"
        init-change (gen-time-before init-time)
        next-change (gen-time-on-interval init-time next-time)
        init-fs {file-path [{:change init-change :content init-content}
                            {:change next-change :content next-content}]}
        context (initial-context (config duration true)
                                 init-fs
                                 read-times)
        expected-content [init-content next-content]
        actual-content [(read! file-path context) (read! file-path context)]
        accesses (get-in context [::sut/file-system :accesses])]
    (t/is (= expected-content actual-content))
    (t/is (= 2 (count @accesses)) "file system was accessed twice")))

(t/deftest outside-duration-no-change
  (let [file-path "path/to/test/file.txt"
        duration (* 30 60)
        init-time (initial-time)
        next-time (gen-time-outside-duration init-time duration)
        read-times (atom [init-time next-time])
        init-content "content"
        init-change (gen-time-before init-time)
        init-fs {file-path [{:change init-change :content init-content}]}
        context (initial-context (config duration)
                                 init-fs
                                 read-times)
        expected-content [init-content init-content]
        actual-content [(read! file-path context) (read! file-path context)]
        accesses (get-in context [::sut/file-system :accesses])]
    (t/is (= expected-content actual-content))
    (t/is (= 2 (count @accesses)) "file system was accessed twice")))

(t/deftest outside-duration-no-change-check-if-newer
  (let [file-path "path/to/test/file.txt"
        duration (* 30 60)
        init-time (initial-time)
        next-time (gen-time-outside-duration init-time duration)
        read-times (atom [init-time next-time])
        init-content "content"
        init-change (gen-time-before init-time)
        init-fs {file-path [{:change init-change :content init-content}]}
        context (initial-context (config duration true)
                                 init-fs
                                 read-times)
        expected-content [init-content init-content]
        actual-content [(read! file-path context) (read! file-path context)]
        accesses (get-in context [::sut/file-system :accesses])]
    (t/is (= expected-content actual-content))
    (t/is (= 1 (count @accesses)) "file system was accessed only once")))

(t/deftest outside-duration-change
  (let [file-path "path/to/test/file.txt"
        duration (* 30 60)
        init-time (initial-time)
        next-time (gen-time-outside-duration init-time duration)
        read-times (atom [init-time next-time])
        init-content "content"
        next-content "changed content"
        init-change (gen-time-before init-time)
        duration-expired-time (time/plus init-time (time/seconds duration))
        next-change (gen-time-on-interval duration-expired-time next-time)
        init-fs {file-path [{:change init-change :content init-content}
                            {:change next-change :content next-content}]}
        context (initial-context (config duration)
                                 init-fs
                                 read-times)
        expected-content [init-content next-content]
        actual-content [(read! file-path context) (read! file-path context)]
        accesses (get-in context [::sut/file-system :accesses])]
    (t/is (= expected-content actual-content))
    (t/is (= 2 (count @accesses)) "file system was accessed twice")))

(t/deftest outside-duration-change-check-if-newer
  (let [file-path "path/to/test/file.txt"
        duration (* 30 60)
        init-time (initial-time)
        next-time (gen-time-outside-duration init-time duration)
        read-times (atom [init-time next-time])
        init-content "content"
        next-content "changed content"
        init-change (gen-time-before init-time)
        duration-expired-time (time/plus init-time (time/seconds duration))
        next-change (gen-time-on-interval duration-expired-time next-time)
        init-fs {file-path [{:change init-change :content init-content}
                            {:change next-change :content next-content}]}
        context (initial-context (config duration true)
                                 init-fs
                                 read-times)
        expected-content [init-content next-content]
        actual-content [(read! file-path context) (read! file-path context)]
        accesses (get-in context [::sut/file-system :accesses])]
    (t/is (= expected-content actual-content))
    (t/is (= 2 (count @accesses)) "file system was accessed twice")))
