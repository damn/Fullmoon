(ns entity.state.player-moving
  (:require [utils.wasd-movement :refer [WASD-movement-vector]]
            [api.entity.state :as state]))

(defrecord PlayerMoving [movement-vector]
  state/PlayerState
  (player-enter [_] [[:tx.context.cursor/set :cursors/walking]])
  (pause-game? [_] false)
  (manual-tick [_ entity* context])
  (clicked-inventory-cell [_ entity* cell])
  (clicked-skillmenu-skill [_ entity* skill])

  state/State
  (enter [_ {:keys [entity/id]} _ctx]
    [[:tx.entity/assoc id :entity/movement-vector movement-vector]])
  (exit [_ {:keys [entity/id]} _ctx]
    [[:tx.entity/dissoc id :entity/movement-vector]])
  (tick [_ {:keys [entity/id]} context]
    (if-let [movement-vector (WASD-movement-vector context)]
      [[:tx.entity/assoc id :entity/movement-vector movement-vector]]
      [[:tx/event id :no-movement-input]]))
  (render-below [_ entity* g ctx])
  (render-above [_ entity* g ctx])
  (render-info  [_ entity* g ctx]))
