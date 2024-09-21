(ns core.entity.player
  (:require [core.component :refer [defcomponent]]
            [core.entity :as entity]
            [core.state :as state]))

(def ^{:doc "Returns the player-entity atom."} entity :context/player-entity)

(defcomponent :entity/player?
  (entity/create [_ eid ctx]
    (assoc ctx entity eid)))

(defn entity*
  "Returns the dereferenced value of the player-entity atom."
  [ctx]
  @(entity ctx))

(defn- state-obj [ctx]
  (-> ctx
      entity*
      entity/state-obj))

(defn update-state      [ctx]       (state/manual-tick             (state-obj ctx) ctx))
(defn state-pause-game? [ctx]       (state/pause-game?             (state-obj ctx)))
(defn clicked-inventory [ctx cell]  (state/clicked-inventory-cell  (state-obj ctx) cell))
(defn clicked-skillmenu [ctx skill] (state/clicked-skillmenu-skill (state-obj ctx) skill))
