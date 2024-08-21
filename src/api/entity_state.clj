(ns api.entity-state)

(defprotocol State
  (enter [_ ctx])
  (exit  [_ ctx])
  (tick  [_ ctx])
  (render-below [_ entity* g ctx])
  (render-above [_ entity* g ctx])
  (render-info  [_ entity* g ctx]))

(defprotocol PlayerState
  (player-enter [_])
  (pause-game? [_])
  ; TODO entity* ...
  (manual-tick [_ entity* ctx])
  (clicked-inventory-cell [_ entity* cell])
  (clicked-skillmenu-skill [_ entity* skill]))
