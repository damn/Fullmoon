(ns components.properties.world
  (:require [core.component :refer [defcomponent]]
            [core.context :as ctx]
            [core.property :refer [def-property-type]]
            mapgen.gen))

(defcomponent :world/player-creature {:data :some #_[:one-to-one :properties/creatures]})

(defcomponent :world/map-size {:data :pos-int})
(defcomponent :world/max-area-level {:data :pos-int}) ; TODO <= map-size !?
(defcomponent :world/spawn-rate {:data :pos}) ; TODO <1 !

(defcomponent :world/tiled-map {:data :string})

(defcomponent :world/components {:data [:map []]})

(defcomponent :world/generator {:data [:enum [:world.generator/tiled-map
                                              :world.generator/modules
                                              :world.generator/uf-caves]]})

(def-property-type :properties/worlds
  {:attributes [:world/generator
                :world/player-creature
                [:world/tiled-map {:optional true}]
                [:world/map-size {:optional true}]
                [:world/max-area-level {:optional true}]
                [:world/spawn-rate {:optional true}]]
   :overview {:title "Worlds"
              :columns 10}})

(defmulti generate (fn [_ctx world] (:world/generator world)))

(defmethod generate :world.generator/tiled-map [ctx world]
  {:tiled-map (ctx/->tiled-map ctx (:world/tiled-map world))
   :start-position [32 71]})

(defmethod generate :world.generator/modules [ctx world]
  (mapgen.gen/generate ctx world))

(defmethod generate :world.generator/uf-caves [ctx world]
  (mapgen.gen/uf-caves ctx world))

(extend-type core.context.Context
 core.context/WorldGenerator
 (->world [ctx world-id]
   (let [prop (ctx/property ctx world-id)]
     (assoc (generate ctx prop)
            :world/player-creature (:world/player-creature prop)))))
