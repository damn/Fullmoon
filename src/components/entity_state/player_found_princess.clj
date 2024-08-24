(ns components.entity-state.player-found-princess
  (:require [core.component :refer [defcomponent]]
            [core.context :as ctx]
            [core.entity-state :as state]))

(defcomponent :player-found-princess
  (state/player-enter [_]
    [[:tx.context.cursor/set :cursors/black-x]])

  (state/pause-game? [_]
    true)

  (state/enter [_ _ctx]
    [[:tx/sound "sounds/bfxr_playerdeath.wav"]
     [:tx/player-modal {:title "YOU WON!"
                        :text "\nYou found the princess!"
                        :button-text ":)"
                        :on-click #(ctx/change-screen % :screens/main-menu)}]]))
