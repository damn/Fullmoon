(ns entity.animation
  (:require [core.component :refer [defcomponent]]
            [data.animation :as animation]
            [api.entity :as entity]
            [api.context :as ctx]
            [core.data :as data]))

(defn- tx-assoc-image-current-frame [{:keys [entity/id entity/animation]}]
  [:tx.entity/assoc id :entity/image (animation/current-frame animation)])

(defcomponent :entity/animation data/animation ; optional
  (entity/create [_ entity* _ctx]
    [(tx-assoc-image-current-frame entity*)])

  (entity/tick [[k animation] {:keys [entity/id] :as entity*} ctx]
    [(tx-assoc-image-current-frame entity*)
     [:tx.entity/assoc id k (animation/tick animation (ctx/delta-time ctx))]]))
