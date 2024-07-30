(ns cdq.api.state)

(defprotocol State
  (enter [_ entity* ctx])
  (exit  [_ entity* ctx])
  (tick  [_ entity* ctx])
  (render-below [_ entity* g ctx])
  (render-above [_ entity* g ctx])
  (render-info  [_ entity* g ctx]))

(defprotocol PlayerState
  (player-enter [_])
  (pause-game? [_])
  (manual-tick [_ entity* ctx])
  (clicked-inventory-cell [_ entity* cell])
  (clicked-skillmenu-skill [_ entity* skill]))
