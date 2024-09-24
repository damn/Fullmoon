(ns ^:no-doc core.ctx.mouseover-entity
  (:require [core.utils.core :refer [sort-by-order]]
            [core.graphics.views :refer [world-mouse-position]]
            [core.entity :as entity]
            [core.entity.player :as player]
            [core.screens.stage :as stage]
            [core.ctx.grid :as grid]))

(defn- calculate-mouseover-entity [ctx]
  (let [player-entity* (player/entity* ctx)
        hits (remove #(= (:z-order %) :z-order/effect) ; or: only items/creatures/projectiles.
                     (map deref
                          (grid/point->entities ctx
                                                (world-mouse-position ctx))))]
    (->> entity/render-order
         (sort-by-order hits :z-order)
         reverse
         (filter #(entity/line-of-sight? ctx player-entity* %))
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
       [:e/dissoc old-entity :entity/mouseover?])
     (when entity
       [:e/assoc entity :entity/mouseover? true])
     (fn [ctx]
       (assoc ctx this-k entity))]))
