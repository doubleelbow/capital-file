(ns user
  (:require [clojure.tools.namespace.repl :refer (refresh refresh-all)]
            [com.doubleelbow.capital.file.alpha :as capital.file]))

(comment
  (require '[clojure.data.json :as json])
  
  (def file-ctx (capital.file/initial-context {::capital.file/base-path "dev"
                                               ::capital.file/read-opts {::capital.file/nonexistent "not found"
                                                                         ::capital.file/cache {::capital.file/use-cache? true
                                                                                               ::capital.file/duration 1800
                                                                                               ::capital.file/check-if-newer? true}
                                                                         ::capital.file/format-config {"json" #(json/read-str % :key-fn keyword)}}}))

  (capital.file/read! "example.edn" file-ctx)
  (capital.file/read! "example.json" file-ctx)
  (capital.file/read! "not-found-example.txt" file-ctx))
