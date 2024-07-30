(ns context.mouseover-entity
  (:require [gdl.context :as ctx :refer [mouse-on-stage-actor?]]
            [utils.core :refer [sort-by-order]]
            [cdq.api.context :refer [world-grid line-of-sight?]]
            [cdq.api.entity :as entity]
            [cdq.api.world.grid :refer [point->entities]]))

(defn- calculate-mouseover-entity [{:keys [context/player-entity] :as context}]
  (let [hits (filter #(:entity/z-order @%)
                     (point->entities (world-grid context)
                                      (ctx/world-mouse-position context)))]
    (->> entity/render-order
         (sort-by-order hits #(:entity/z-order @%))
         reverse
         (filter #(line-of-sight? context @player-entity @%))
         first)))

(extend-type gdl.context.Context
  cdq.api.context/MouseOverEntity
  (update-mouseover-entity! [{:keys [context/mouseover-entity]
                              :as context}]
    (when-let [entity @mouseover-entity]
      (swap! entity dissoc :entity/mouseover?))
    (let [entity (if (mouse-on-stage-actor? context)
                   nil
                   (calculate-mouseover-entity context))]
      (reset! mouseover-entity entity)
      (when entity
        (swap! entity assoc :entity/mouseover? true)))))

(defn ->context []
  {:context/mouseover-entity (atom nil)})
