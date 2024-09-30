(ns core.app
  (:require [clojure.gdx :refer :all]
            core.config
            core.creature
            [core.entity :as entity]
            core.stat
            [core.world :as world])
  (:load "screens/minimap"
         "screens/world"
         "screens/main_menu"
         "screens/options"))

(defn -main []
  (start-app! "resources/properties.edn"))
