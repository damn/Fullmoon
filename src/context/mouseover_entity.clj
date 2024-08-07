(ns context.mouseover-entity
  (:require [utils.core :refer [sort-by-order]]
            [api.context :as ctx :refer [mouse-on-stage-actor? world-grid line-of-sight?]]
            [api.entity :as entity]
            [api.world.grid :refer [point->entities]]))

(defn- calculate-mouseover-entity [{:keys [context/player-entity] :as context}]
  (let [hits (filter #(:entity/z-order @%)
                     (point->entities (world-grid context)
                                      (ctx/world-mouse-position context)))]
    (->> entity/render-order
         (sort-by-order hits #(:entity/z-order @%))
         reverse
         (filter #(line-of-sight? context @player-entity @%))
         first)))

(defn- mouseover-entity-atom [ctx]
  (-> ctx
      :context/game
      :context.game/state
      :mouseover-entity))

(extend-type api.context.Context
  api.context/MouseOverEntity
  (mouseover-entity* [ctx]
    (when-let [entity @(mouseover-entity-atom ctx)]
      @entity))

  (update-mouseover-entity! [ctx]
    (let [entity-ref (mouseover-entity-atom ctx)]
      (when-let [entity @entity-ref]
        (swap! entity dissoc :entity/mouseover?))
      (let [entity (if (mouse-on-stage-actor? ctx)
                     nil
                     (calculate-mouseover-entity ctx))]
        (reset! entity-ref entity)
        (when entity
          (swap! entity assoc :entity/mouseover? true))))))
