(ns components.properties.world
  (:require [core.component :refer [defcomponent] :as component]
            [core.context :as ctx]
            mapgen.module-gen))

; [:world/map-size data/pos-int-attr]
; [:world/max-area-level data/pos-int-attr] ; TODO <= map-size !?
; [:world/princess {:data [:qualified-keyword {:namespace :creatures}]}]
; [:world/spawn-rate data/pos-attr] ; TODO <1 !

(defcomponent :world/type {:data [:enum
                                  :world.type/tiled-map
                                  :world.type/modules
                                  :world.type/uf-caves]})

(defcomponent :properties/world
  (component/create [_ _ctx]
    {:id-namespace "worlds"
     :schema [[:property/id [:qualified-keyword {:namespace :worlds}]]
              ;[:world/generator]
              [:world/type]

              ]
     :edn-file-sort-order 5
     :overview {:title "Worlds"
                :columns 10
                :image/dimensions [96 96]}}))

(defmulti generate (fn [ctx world] (:world/type world)))

(defmethod generate :world.type/tiled-map [ctx _world]
  {:tiled-map (ctx/->tiled-map ctx "maps/vampire.tmx")
   :start-position [32 71]})

(defmethod generate :world.type/modules [ctx world]
  (let [{:keys [tiled-map
                start-position]} (mapgen.module-gen/generate ctx
                                                             {:world/map-size 3,
                                                              :world/max-area-level 3,
                                                              :world/princess :creatures/lady-a,
                                                              :world/spawn-rate 0.05})]
    {:tiled-map tiled-map
     :start-position start-position}))

(defmethod generate :world.type/uf-caves [ctx world]
  (mapgen.module-gen/uf-caves ctx {:world/map-size 250
                                   :world/spawn-rate 0.02}))

(extend-type core.context.Context
 core.context/WorldGenerator
 (->world [ctx world-id]
   (generate ctx (ctx/get-property ctx world-id))))
