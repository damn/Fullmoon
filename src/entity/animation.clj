(ns entity.animation
  (:require [core.component :refer [defcomponent]]
            [data.animation :as animation]
            [api.entity :as entity]
            [api.context :as ctx]
            [core.data :as data]))

(defn- tx-assoc-image-current-frame [eid animation]
  [:tx.entity/assoc eid :entity/image (animation/current-frame animation)])

(defcomponent :entity/animation data/animation ; optional
  animation
  (entity/create [_ eid _ctx]
    [(tx-assoc-image-current-frame eid animation)])

  (entity/tick [[k _] eid ctx]
    [(tx-assoc-image-current-frame eid animation)
     [:tx.entity/assoc eid k (animation/tick animation (ctx/delta-time ctx))]]))
