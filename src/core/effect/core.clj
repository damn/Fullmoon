(ns core.effect.core
  (:require [core.math.vector :as v]
            [core.entity :as entity]
            [core.graphics.views :refer [world-mouse-position]]
            [core.ctx.mouseover-entity :as mouseover]
            [core.ctx.grid :as grid]))

(defn- nearest-enemy [{:keys [context/grid]} entity*]
  (grid/nearest-entity @(grid (entity/tile entity*))
                       (entity/enemy-faction entity*)))

(defn ->npc-effect-ctx [ctx entity*]
  (let [target (nearest-enemy ctx entity*)
        target (when (and target (entity/line-of-sight? ctx entity* @target))
                 target)]
    {:effect/source (:entity/id entity*)
     :effect/target target
     :effect/direction (when target (entity/direction entity* @target))}))

(defn ->player-effect-ctx [ctx entity*]
  (let [target* (mouseover/entity* ctx)
        target-position (or (and target* (:position target*))
                            (world-mouse-position ctx))]
    {:effect/source (:entity/id entity*)
     :effect/target (:entity/id target*)
     :effect/target-position target-position
     :effect/direction (v/direction (:position entity*) target-position)}))

