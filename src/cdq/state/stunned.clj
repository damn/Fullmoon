(ns cdq.state.stunned
  (:require [gdl.graphics :as g]
            [cdq.api.context :refer [stopped? ->counter]]
            [cdq.api.state :as state]))

(defrecord Stunned [counter]
  state/PlayerState
  (player-enter [_] [[:tx/cursor :cursors/denied]])
  (pause-game? [_] false)
  (manual-tick [_ _entity* context])
  (clicked-inventory-cell [_ entity* cell])
  (clicked-skillmenu-skill [_ entity* skill])

  state/State
  (enter [_ _entity* _ctx])
  (exit  [_ _entity* _ctx])
  (tick [_ entity* ctx]
    (when (stopped? ctx counter)
      [[:tx/event (:entity/id entity*) :effect-wears-off]]))

  (render-below [_ {:keys [entity/position]} g _ctx]
    (g/draw-circle g position 0.5 [1 1 1 0.6]))

  (render-above [_ entity* g ctx])
  (render-info  [_ entity* g ctx]))

(defn ->CreateWithCounter [ctx _entity* duration]
  (->Stunned (->counter ctx duration)))
