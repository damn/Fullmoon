(ns app
  (:require [clojure.edn :as edn]
            [app.start :as app]))

(defn -main []
  (-> "resources/app.edn"
      slurp
      edn/read-string
      app/start))
