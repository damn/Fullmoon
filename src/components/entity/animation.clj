(ns components.entity.animation
  (:require [core.component :as component :refer [defcomponent]]
            [core.animation :as animation]
            [core.context :as ctx]))

(defn- tx-assoc-image-current-frame [eid animation]
  [:tx/assoc eid :entity/image (animation/current-frame animation)])

(defcomponent :entity/animation
  {:data :animation
   :optional? false
   :let animation}
  (component/create-e [_ eid _ctx]
    [(tx-assoc-image-current-frame eid animation)])

  (component/tick [[k _] eid ctx]
    [(tx-assoc-image-current-frame eid animation)
     [:tx/assoc eid k (animation/tick animation (ctx/delta-time ctx))]]))

(defcomponent :entity/delete-after-animation-stopped?
  (component/create-e [_ entity _ctx]
    (-> @entity :entity/animation :looping? not assert))

  (component/tick [_ entity _ctx]
    (when (animation/stopped? (:entity/animation @entity))
      [[:tx/destroy entity]])))
