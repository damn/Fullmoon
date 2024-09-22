(ns core.entity.state.player-dead
  (:require [core.component :refer [defcomponent]]
            [core.screens :as screens]
            [core.entity.state :as state]))

(defcomponent :player-dead
  (state/player-enter [_]
    [[:tx/cursor :cursors/black-x]])

  (state/pause-game? [_]
    true)

  (state/enter [_ _ctx]
    [[:tx/sound "sounds/bfxr_playerdeath.wav"]
     [:tx/player-modal {:title "YOU DIED"
                        :text "\nGood luck next time"
                        :button-text ":("
                        :on-click #(screens/change-screen % :screens/main-menu)}]]))
