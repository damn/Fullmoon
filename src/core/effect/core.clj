(ns core.effect.core
  (:require [math.vector :as v]
            [core.context :as ctx]
            [core.entity :as entity]
            [core.graphics.views :refer [world-mouse-position]]
            [core.world.cell :as cell]))

(defn- nearest-enemy [ctx entity*]
  (cell/nearest-entity @((ctx/world-grid ctx) (entity/tile entity*))
                       (entity/enemy-faction entity*)))

(defn ->npc-effect-ctx [ctx entity*]
  (let [target (nearest-enemy ctx entity*)
        target (when (and target (ctx/line-of-sight? ctx entity* @target))
                 target)]
    (ctx/map->Context
     {:effect/source (:entity/id entity*)
      :effect/target target
      :effect/direction (when target (entity/direction entity* @target))})))

(defn ->player-effect-ctx [ctx entity*]
  (let [target* (ctx/mouseover-entity* ctx)
        target-position (or (and target* (:position target*))
                            (world-mouse-position ctx))]
    (ctx/map->Context
     {:effect/source (:entity/id entity*)
      :effect/target (:entity/id target*)
      :effect/target-position target-position
      :effect/direction (v/direction (:position entity*) target-position)})))

