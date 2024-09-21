(ns core.mouseover-entity
  (:require [utils.core :refer [sort-by-order]]
            [core.context :as ctx :refer [mouse-on-stage-actor? world-grid line-of-sight?]]
            [core.entity :as entity]
            [core.world.grid :refer [point->entities]]))

(defn- calculate-mouseover-entity [context]
  (let [player-entity* (ctx/player-entity* context)
        hits (remove #(= (:z-order %) :z-order/effect) ; or: only items/creatures/projectiles.
                     (map deref
                          (point->entities (world-grid context)
                                           (ctx/world-mouse-position context))))]
    (->> entity/render-order
         (sort-by-order hits :z-order)
         reverse
         (filter #(line-of-sight? context player-entity* %))
         first
         :entity/id)))

(def ^:private this-k :context/mouseover-entity)

(extend-type core.context.Context
  core.context/MouseOverEntity
  (mouseover-entity* [ctx]
    (when-let [entity (this-k ctx)]
      @entity))

  (update-mouseover-entity [ctx]
    (let [entity (if (mouse-on-stage-actor? ctx)
                   nil
                   (calculate-mouseover-entity ctx))]
      [(when-let [old-entity (this-k ctx)]
         [:tx/dissoc old-entity :entity/mouseover?])
       (when entity
         [:tx/assoc entity :entity/mouseover? true])
       (fn [ctx]
         (assoc ctx this-k entity))])))
