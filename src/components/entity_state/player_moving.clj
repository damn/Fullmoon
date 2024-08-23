(ns components.entity-state.player-moving
  (:require [utils.wasd-movement :refer [WASD-movement-vector]]
            [core.entity :as entity]
            [core.entity-state :as state]))

(defrecord PlayerMoving [eid movement-vector]
  state/PlayerState
  (player-enter [_] [[:tx.context.cursor/set :cursors/walking]])
  (pause-game? [_] false)
  (manual-tick [_ context])
  (clicked-inventory-cell [_ cell])
  (clicked-skillmenu-skill [_ skill])

  state/State
  (enter [_ _ctx]
    [[:tx.entity/set-movement eid {:direction movement-vector
                                   :speed (entity/stat @eid :stats/movement-speed)}]])

  (exit [_ _ctx]
    [[:tx.entity/set-movement eid nil]])

  (tick [_ context]
    (let [entity* @eid]
      (if-let [movement-vector (WASD-movement-vector)]
        [[:tx.entity/set-movement eid {:direction movement-vector
                                       :speed (entity/stat entity* :stats/movement-speed)}]]
        [[:tx/event eid :no-movement-input]])))

  (render-below [_ entity* g ctx])
  (render-above [_ entity* g ctx])
  (render-info  [_ entity* g ctx]))

(defn ->build [ctx eid movement-vector]
  (->PlayerMoving eid movement-vector))
