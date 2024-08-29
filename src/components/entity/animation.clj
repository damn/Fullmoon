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
  (component/edn->value [_ ctx] (animation/edn->animation animation ctx))
  (component/value->edn [_]     (animation/animation->edn animation))

  (component/create-e [_ eid _ctx]
    [(tx-assoc-image-current-frame eid animation)])

  (component/tick [[k _] eid ctx]
    [(tx-assoc-image-current-frame eid animation)
     [:tx/assoc eid k (animation/tick animation (ctx/delta-time ctx))]]))
