(defproject com.doubleelbow.capital/capital-file "0.1.0-SNAPSHOT"
  :description "capital library for file manipulation"
  :url "https://github.com/doubleelbow/capital-file"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[com.doubleelbow.capital/capital "0.1.0-SNAPSHOT"]
                 [io.pedestal/pedestal.log "0.5.4"]
                 [pathetic "0.5.1"]
                 [clj-time "0.14.4"]]
  :profiles {:dev {:source-paths ["dev"]
                   :dependencies [[org.clojure/tools.namespace "0.3.0-alpha1"]
                                  [ch.qos.logback/logback-classic "1.1.7" :exclusions [org.slf4j/slf4j-api]]
                                  [org.slf4j/jul-to-slf4j "1.7.21"]
                                  [org.slf4j/jcl-over-slf4j "1.7.21"]
                                  [org.slf4j/log4j-over-slf4j "1.7.21"]
                                  [org.clojure/data.json "0.2.6"]]
                   :plugins [[com.doubleelbow/lein-deploy-prepared "0.1.0"]]}}
  :repositories [["snapshots" {:url "https://repo.clojars.org"
                               :username :env/clojars_user
                               :password :env/clojars_pass}]
                 ["releases" {:url "https://repo.clojars.org"
                              :username :env/clojars_user
                              :password :env/clojars_pass}]])
