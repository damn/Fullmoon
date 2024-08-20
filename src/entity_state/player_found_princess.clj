(ns entity-state.player-found-princess
  (:require [app :refer [change-screen!]]
            [api.entity-state :as state]))

(defrecord PlayerFoundPrincess []
  state/PlayerState
  (player-enter [_] [[:tx.context.cursor/set :cursors/black-x]])
  (pause-game? [_] true)
  (manual-tick [_ entity* context])
  (clicked-inventory-cell [_ entity* cell])
  (clicked-skillmenu-skill [_ entity* skill])

  state/State
  (enter [_ _entity* _ctx]
    [[:tx/sound "sounds/bfxr_playerdeath.wav"]
     [:tx/player-modal {:title "YOU WON!"
                        :text "\nYou found the princess!"
                        :button-text ":)"
                        :on-click (fn [_ctx]
                                    (change-screen! :screens/main-menu))}]])
  (exit [_ entity* _ctx])
  (tick [_ entity* _ctx])
  (render-below [_ entity* g ctx])
  (render-above [_ entity* g ctx])
  (render-info  [_ entity* g ctx]))
