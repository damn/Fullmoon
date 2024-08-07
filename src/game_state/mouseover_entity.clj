(ns game-state.mouseover-entity
  (:require [utils.core :refer [sort-by-order]]
            [api.context :as ctx :refer [mouse-on-stage-actor? world-grid line-of-sight?]]
            [api.entity :as entity]
            [api.world.grid :refer [point->entities]]))

(defn- calculate-mouseover-entity [context]
  (let [player-entity* (ctx/player-entity* context)
        hits (filter #(:entity/z-order @%)
                     (point->entities (world-grid context)
                                      (ctx/world-mouse-position context)))]
    (->> entity/render-order
         (sort-by-order hits #(:entity/z-order @%))
         reverse
         (filter #(line-of-sight? context player-entity* @%))
         first)))

(defn ->state []
  {:mouseover-entity (atom nil)})

(defn- state [ctx]
  (-> ctx :context/game :mouseover-entity))

(extend-type api.context.Context
  api.context/MouseOverEntity
  (mouseover-entity* [ctx]
    (when-let [entity @(state ctx)]
      @entity))

  (update-mouseover-entity! [ctx]
    (let [entity-ref (state ctx)]
      (when-let [entity @entity-ref]
        (swap! entity dissoc :entity/mouseover?))
      (let [entity (if (mouse-on-stage-actor? ctx)
                     nil
                     (calculate-mouseover-entity ctx))]
        (reset! entity-ref entity)
        (when entity
          (swap! entity assoc :entity/mouseover? true))))))
