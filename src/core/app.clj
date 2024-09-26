(ns core.app
  (:require [clojure.ctx :refer [start-app!]]
            core.creature
            core.projectile
            core.screens
            core.stat
            core.skill))

(defn -main []
  (start-app! "resources/properties.edn"))
