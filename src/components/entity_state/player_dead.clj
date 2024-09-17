(ns components.entity-state.player-dead
  (:require [core.component :as component :refer [defcomponent]]
            [core.context :as ctx]))

(defcomponent :player-dead
  (component/player-enter [_]
    [[:tx/cursor :cursors/black-x]])

  (component/pause-game? [_]
    true)

  (component/enter [_ _ctx]
    [[:tx/sound "sounds/bfxr_playerdeath.wav"]
     [:tx/player-modal {:title "YOU DIED"
                        :text "\nGood luck next time"
                        :button-text ":("
                        :on-click #(ctx/change-screen % :screens/main-menu)}]]))
