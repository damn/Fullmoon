(ns entity.player
  (:require [core.component :refer [defcomponent]]
            [api.context :as ctx]
            [api.entity :as entity]
            [api.entity-state :as state]))

(defcomponent :entity/player? {}
  (entity/create [_ eid ctx]
    (assoc ctx ::eid eid)))

(defn- state-obj [ctx]
  (-> ctx
      ctx/player-entity*
      entity/state-obj))

(extend-type api.context.Context
  api.context/PlayerEntity
  (player-entity [ctx]
    (::eid ctx))

  (player-entity* [ctx]
    @(ctx/player-entity ctx))

  (player-update-state      [ctx]       (state/manual-tick             (state-obj ctx) ctx))
  (player-state-pause-game? [ctx]       (state/pause-game?             (state-obj ctx)))
  (player-clicked-inventory [ctx cell]  (state/clicked-inventory-cell  (state-obj ctx) cell))
  (player-clicked-skillmenu [ctx skill] (state/clicked-skillmenu-skill (state-obj ctx) skill)))
