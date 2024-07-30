(ns context.ui.help-window
  (:require [gdl.context :as ctx :refer [->window ->label]]))

(def ^:private controls-text
  "* Moving: WASD-keys
   * Use a skill: click leftmouse. Select in actionbar below.

  * S   - skillmenu
  * C   - character info
  * I   - inventory
  * H   - help
  * ESC - exit/close menu
  * P   - Pause the game")

(defn create [ctx]
  (->window ctx
            {:id :help-window
             :title "Controls"
             :visible? false
             :center-position [(/ (ctx/gui-viewport-width ctx) 2)
                               (ctx/gui-viewport-height ctx)]
             :rows [[(->label ctx controls-text)]]
             :pack? true}))
