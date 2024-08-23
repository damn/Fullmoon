(ns components.entity-state.player-dead
  (:require [core.component :refer [defcomponent]]
            [core.context :as ctx]
            [core.entity-state :as state]))

(defcomponent :player-dead {}
  (state/player-enter [_]
    [[:tx.context.cursor/set :cursors/black-x]])

  (state/pause-game? [_]
    true)

  (state/enter [_ _ctx]
    [[:tx/sound "sounds/bfxr_playerdeath.wav"]
     [:tx/player-modal {:title "YOU DIED"
                        :text "\nGood luck next time"
                        :button-text ":("
                        :on-click #(ctx/change-screen % :screens/main-menu)}]]))
