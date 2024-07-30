(ns cdq.entity.delete-after-animation-stopped
  (:require [core.component :as component]
            [gdl.graphics.animation :as animation]
            [cdq.api.entity :as entity]))

(component/def :entity/delete-after-animation-stopped? {}
  _
  (entity/create [_ entity* _ctx]
    (-> entity* :entity/animation :looping? not assert))
  (entity/tick [_ {:keys [entity/id entity/animation]} _ctx]
    (when (animation/stopped? animation)
      [[:tx/destroy id]])))
