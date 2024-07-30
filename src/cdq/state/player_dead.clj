(ns cdq.state.player-dead
  (:require [gdl.app :refer [change-screen!]]
            [cdq.api.state :as state]))

(defrecord PlayerDead []
  state/PlayerState
  (player-enter [_] [[:tx/cursor :cursors/black-x]])
  (pause-game? [_] true)
  (manual-tick [_ entity* context])
  (clicked-inventory-cell [_ entity* cell])
  (clicked-skillmenu-skill [_ entity* skill])

  state/State
  (enter [_ _entity* _ctx]
    [[:tx/sound  "sounds/bfxr_playerdeath.wav"]
     [:tx/player-modal {:title "YOU DIED"
                        :text "\nGood luck next time"
                        :button-text ":("
                        :on-click (fn [_ctx]
                                    (change-screen! :screens/main-menu))}]])
  (exit [_ entity* _ctx])
  (tick [_ entity* _ctx])
  (render-below [_ entity* g ctx])
  (render-above [_ entity* g ctx])
  (render-info  [_ entity* g ctx]))
