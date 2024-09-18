(ns components.properties.audiovisual
  (:require [core.component :refer [defcomponent]]
            [core.context :as ctx]
            [core.entity :as entity]
            [core.property :refer [def-property-type]]
            [core.tx :as tx]))

(def-property-type :properties/audiovisuals
  {:schema [:tx/sound
            :entity/animation]
   :overview {:title "Audiovisuals"
              :columns 10
              :image/scale 2}})

(defcomponent :tx/audiovisual
  (tx/do! [[_ position id] ctx]
    (let [{:keys [tx/sound
                  entity/animation]} (ctx/property ctx id)]
      [[:tx/sound sound]
       [:tx/create
        position
        entity/effect-body-props
        {:entity/animation animation
         :entity/delete-after-animation-stopped? true}]])))
