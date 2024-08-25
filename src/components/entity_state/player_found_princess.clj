(ns components.entity-state.player-found-princess
  (:require [core.component :as component :refer [defcomponent]]
            [core.context :as ctx]))

(defcomponent :player-found-princess
  (component/player-enter [_]
    [[:tx.context.cursor/set :cursors/black-x]])

  (component/pause-game? [_]
    true)

  (component/enter [_ _ctx]
    [[:tx/sound "sounds/bfxr_playerdeath.wav"]
     [:tx/player-modal {:title "YOU WON!"
                        :text "\nYou found the princess!"
                        :button-text ":)"
                        :on-click #(ctx/change-screen % :screens/main-menu)}]]))
