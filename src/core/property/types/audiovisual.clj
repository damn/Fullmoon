(ns ^:no-doc core.property.types.audiovisual
  (:require [core.component :as component]
            [core.ctx :refer :all]
            [core.entity :as entity]
            [core.ctx.property :as property]))

(property/def-type :properties/audiovisuals
  {:schema [:tx/sound
            :entity/animation]
   :overview {:title "Audiovisuals"
              :columns 10
              :image/scale 2}})

(defcomponent :tx/audiovisual
  (component/do! [[_ position id] ctx]
    (let [{:keys [tx/sound
                  entity/animation]} (property/build ctx id)]
      [[:tx/sound sound]
       [:e/create
        position
        entity/effect-body-props
        {:entity/animation animation
         :entity/delete-after-animation-stopped? true}]])))
