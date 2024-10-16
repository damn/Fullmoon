(ns property.audiovisual
  (:require [core.component :refer [defc]]
            [core.db :as db]
            [core.tx :as tx]
            [world.entity :as entity]))

(db/def-property :properties/audiovisuals
  {:schema [:tx/sound
            :entity/animation]
   :overview {:title "Audiovisuals"
              :columns 10
              :image/scale 2}})

(defc :tx/audiovisual
  (tx/do! [[_ position id]]
    (let [{:keys [tx/sound entity/animation]} (db/get id)]
      [[:tx/sound sound]
       [:e/create
        position
        entity/effect-body-props
        {:entity/animation animation
         :entity/delete-after-animation-stopped? true}]])))

(defc :entity/destroy-audiovisual
  {:let audiovisuals-id}
  (entity/destroy [_ eid]
    [[:tx/audiovisual (:position @eid) audiovisuals-id]]))

