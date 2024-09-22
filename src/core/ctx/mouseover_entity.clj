(ns core.ctx.mouseover-entity
  (:require [utils.core :refer [sort-by-order]]
            [core.world :refer [world-grid]]
            [core.graphics.views :refer [world-mouse-position]]
            [core.entity :as entity]
            [core.entity.player :as player]
            [core.line-of-sight :refer [line-of-sight?]]
            [core.screens.stage :as stage]
            [core.ctx.grid :as grid]))

(defn- calculate-mouseover-entity [context]
  (let [player-entity* (player/entity* context)
        hits (remove #(= (:z-order %) :z-order/effect) ; or: only items/creatures/projectiles.
                     (map deref
                          (grid/point->entities (world-grid context)
                                                (world-mouse-position context))))]
    (->> entity/render-order
         (sort-by-order hits :z-order)
         reverse
         (filter #(line-of-sight? context player-entity* %))
         first
         :entity/id)))

(def ^:private this-k :context/mouseover-entity)

(defn entity* [ctx]
  (when-let [entity (this-k ctx)]
    @entity))

(defn update-mouseover-entity [ctx]
  (let [entity (if (stage/mouse-on-actor? ctx)
                 nil
                 (calculate-mouseover-entity ctx))]
    [(when-let [old-entity (this-k ctx)]
       [:tx/dissoc old-entity :entity/mouseover?])
     (when entity
       [:tx/assoc entity :entity/mouseover? true])
     (fn [ctx]
       (assoc ctx this-k entity))]))
