(ns world.mouseover-entity
  (:require [clojure.gdx.graphics :as g]
            [clojure.gdx.ui.stage-screen :refer [mouse-on-actor?]]
            [utils.core :refer [sort-by-order]]
            [world.entity.body :refer [line-of-sight? render-order]]
            [world.grid :as grid]
            [world.player :refer [world-player]]))

(def mouseover-entity nil) ; private ?!

(defn mouseover-entity* []
  (when-let [entity mouseover-entity]
    @entity))

(defn- calculate-mouseover-entity []
  (let [player-entity* @world-player
        hits (remove #(= (:z-order %) :z-order/effect) ; or: only items/creatures/projectiles.
                     (map deref
                          (grid/point->entities (g/world-mouse-position))))]
    (->> render-order
         (sort-by-order hits :z-order)
         reverse
         (filter #(line-of-sight? player-entity* %))
         first
         :entity/id)))

(defn update! []
  (let [entity (if (mouse-on-actor?)
                 nil
                 (calculate-mouseover-entity))]
    [(when-let [old-entity mouseover-entity]
       [:e/dissoc old-entity :entity/mouseover?])
     (when entity
       [:e/assoc entity :entity/mouseover? true])
     (fn []
       (.bindRoot #'mouseover-entity entity)
       nil)]))
