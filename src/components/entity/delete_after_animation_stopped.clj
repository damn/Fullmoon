(ns components.entity.delete-after-animation-stopped
  (:require [core.component :as component :refer [defcomponent]]
            [core.animation :as animation]))

(defcomponent :entity/delete-after-animation-stopped?
  (component/create-e [_ entity _ctx]
    (-> @entity :entity/animation :looping? not assert))

  (component/tick [_ entity _ctx]
    (when (animation/stopped? (:entity/animation @entity))
      [[:tx/destroy entity]])))
