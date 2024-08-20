(ns entity-state.player-moving
  (:require [utils.wasd-movement :refer [WASD-movement-vector]]
            [api.entity :as entity]
            [api.entity-state :as state]))

(defrecord PlayerMoving [movement-vector]
  state/PlayerState
  (player-enter [_] [[:tx.context.cursor/set :cursors/walking]])
  (pause-game? [_] false)
  (manual-tick [_ entity* context])
  (clicked-inventory-cell [_ entity* cell])
  (clicked-skillmenu-skill [_ entity* skill])

  state/State
  (enter [_ {:keys [entity/id] :as entity*} _ctx]
    [[:tx.entity/set-movement id {:direction movement-vector
                                  :speed (entity/stat entity* :stats/movement-speed)}]])

  (exit [_ {:keys [entity/id]} _ctx]
    [[:tx.entity/set-movement id nil]])

  (tick [_ entity context]
    (let [{:keys [entity/id] :as entity*} @entity]
      (if-let [movement-vector (WASD-movement-vector context)]
        [[:tx.entity/set-movement id {:direction movement-vector
                                      :speed (entity/stat entity* :stats/movement-speed)}]]
        [[:tx/event id :no-movement-input]])))

  (render-below [_ entity* g ctx])
  (render-above [_ entity* g ctx])
  (render-info  [_ entity* g ctx]))
