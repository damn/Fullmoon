(ns entity-state.stunned
  (:require [api.graphics :as g]
            [api.context :refer [stopped? ->counter]]
            [api.entity :as entity]
            [api.entity-state :as state]))

(defrecord Stunned [counter]
  state/PlayerState
  (player-enter [_] [[:tx.context.cursor/set :cursors/denied]])
  (pause-game? [_] false)
  (manual-tick [_ _entity* context])
  (clicked-inventory-cell [_ entity* cell])
  (clicked-skillmenu-skill [_ entity* skill])

  state/State
  (enter [_ _entity _ctx])
  (exit  [_ _entity _ctx])
  (tick [_ eid ctx]
    (when (stopped? ctx counter)
      [[:tx/event eid :effect-wears-off]]))

  (render-below [_ entity* g _ctx]
    (g/draw-circle g (entity/position entity*) 0.5 [1 1 1 0.6]))

  (render-above [_ entity* g ctx])
  (render-info  [_ entity* g ctx]))

(defn ->CreateWithCounter [ctx _entity* duration]
  (->Stunned (->counter ctx duration)))
