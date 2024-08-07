(ns game-state.mouseover-entity
  (:require [utils.core :refer [sort-by-order]]
            [api.context :as ctx :refer [mouse-on-stage-actor? world-grid line-of-sight?]]
            [api.entity :as entity]
            [api.world.grid :refer [point->entities]]))

(defn ->state []
  {:mouseover-entity nil})

(defn- mouseover-entity [ctx]
  (-> ctx
      :context/game
      deref
      :mouseover-entity))

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

(extend-type api.context.Context
  api.context/MouseOverEntity
  (mouseover-entity* [ctx]
    (when-let [entity (mouseover-entity ctx)]
      @entity)))

(defn update! [game-state* ctx]
  (when-let [entity (:mouseover-entity game-state*)]
    (swap! entity dissoc :entity/mouseover?))
  (let [entity (if (mouse-on-stage-actor? ctx)
                 nil
                 (calculate-mouseover-entity ctx))]
    (when entity
      (swap! entity assoc :entity/mouseover? true))
    (assoc game-state* :mouseover-entity entity)))
