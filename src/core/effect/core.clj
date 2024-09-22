(ns core.effect.core
  (:require [math.vector :as v]
            [core.entity :as entity]
            [core.graphics.views :refer [world-mouse-position]]
            [core.mouseover-entity :as mouseover]
            [core.line-of-sight :refer [line-of-sight?]]
            [core.world :refer [world-grid]]
            [core.world.cell :as cell]))

(defn- nearest-enemy [ctx entity*]
  (cell/nearest-entity @((world-grid ctx) (entity/tile entity*))
                       (entity/enemy-faction entity*)))

(defn ->npc-effect-ctx [ctx entity*]
  (let [target (nearest-enemy ctx entity*)
        target (when (and target (line-of-sight? ctx entity* @target))
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

