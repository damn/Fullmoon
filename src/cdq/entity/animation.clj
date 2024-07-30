(ns cdq.entity.animation
  (:require [core.component :as component]
            [gdl.graphics.animation :as animation]
            [cdq.api.entity :as entity]
            [cdq.attributes :as attr]))

(defn- tx-assoc-image-current-frame [{:keys [entity/id entity/animation]}]
  [:tx/assoc id :entity/image (animation/current-frame animation)])

(component/def :entity/animation attr/animation ; optional
  animation
  (entity/create [_ entity* _ctx]
    [(tx-assoc-image-current-frame entity*)])
  (entity/tick [[k _] {:keys [entity/id] :as entity*} {:keys [context/delta-time]}]
    [(tx-assoc-image-current-frame entity*)
     [:tx/assoc id k (animation/tick animation delta-time)]]))
