(ns components.entity.player
  (:require [core.component :as component :refer [defcomponent]]
            [core.context :as ctx]
            [core.entity :as entity]))

(defcomponent :entity/player?
  (component/create-e [_ eid ctx]
    (assoc ctx ::eid eid)))

(defn- state-obj [ctx]
  (-> ctx
      ctx/player-entity*
      entity/state-obj))

(extend-type core.context.Context
  core.context/PlayerEntity
  (player-entity [ctx]
    (::eid ctx))

  (player-entity* [ctx]
    @(ctx/player-entity ctx))

  (player-update-state      [ctx]       (component/manual-tick             (state-obj ctx) ctx))
  (player-state-pause-game? [ctx]       (component/pause-game?             (state-obj ctx)))
  (player-clicked-inventory [ctx cell]  (component/clicked-inventory-cell  (state-obj ctx) cell))
  (player-clicked-skillmenu [ctx skill] (component/clicked-skillmenu-skill (state-obj ctx) skill)))
