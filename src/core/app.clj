(ns core.app
  (:require [clojure.world :refer [start-app!]]
            core.creature
            core.projectile
            core.screens
            core.skill))

(defn -main []
  (start-app! "resources/properties.edn"))
