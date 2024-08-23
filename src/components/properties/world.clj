(ns components.properties.world
  (:require [core.component :refer [defcomponent] :as component]
            [core.data :as data]))

(defcomponent :properties/world {}
  (component/create [_ _ctx]
    (defcomponent :world/map-size data/pos-int-attr)
    (defcomponent :world/max-area-level data/pos-int-attr) ; TODO <= map-size !?
    (defcomponent :world/princess {:schema [:qualified-keyword {:namespace :creatures}]})
    (defcomponent :world/spawn-rate data/pos-attr) ; TODO <1 !
    {:id-namespace "worlds"
     :schema (data/map-attribute-schema
              [:property/id [:qualified-keyword {:namespace :worlds}]]
              [:world/map-size
               :world/max-area-level
               :world/princess
               :world/spawn-rate])
     :edn-file-sort-order 5
     :overview {:title "Worlds"
                :columns 10
                :image/dimensions [96 96]}}))
