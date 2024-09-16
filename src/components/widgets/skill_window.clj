(ns components.widgets.skill-window
  (:require [core.components :as components]
            [core.context :as ctx :refer [->window ->image-button]]
            [gdx.scene2d.actor :refer [add-tooltip!]]))

; TODO render text label free-skill-points
; (str "Free points: " (:entity/free-skill-points @player-entity))
(defn create [context]
  (->window context
            {:title "Skills"
             :id :skill-window
             :visible? false
             :cell-defaults {:pad 10}
             :rows [(for [id [:skills/projectile
                              :skills/meditation
                              :skills/spawn
                              :skills/melee-attack]
                          :let [; get-property in callbacks if they get changed, this is part of context permanently
                                button (->image-button context ; TODO reuse actionbar button scale?
                                                       (:entity/image (ctx/property context id)) ; TODO here anyway taken
                                                       ; => should probably build this window @ game start
                                                       (fn [ctx]
                                                         (ctx/do! ctx (ctx/player-clicked-skillmenu ctx (ctx/property ctx id)))))]]
                      (do
                       (add-tooltip! button #(components/info-text (ctx/property % id) %)) ; TODO no player modifiers applied (see actionbar)
                       button))]
             :pack? true}))
