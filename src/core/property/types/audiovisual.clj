(ns core.property.types.audiovisual
  (:require [core.component :refer [defcomponent]]
            [core.entity :as entity]
            [core.ctx.property :as property]
            [core.tx :as tx]))

(property/def-type :properties/audiovisuals
  {:schema [:tx/sound
            :entity/animation]
   :overview {:title "Audiovisuals"
              :columns 10
              :image/scale 2}})

(defcomponent :tx/audiovisual
  (tx/do! [[_ position id] ctx]
    (let [{:keys [tx/sound
                  entity/animation]} (property/build ctx id)]
      [[:tx/sound sound]
       [:tx/create
        position
        entity/effect-body-props
        {:entity/animation animation
         :entity/delete-after-animation-stopped? true}]])))
