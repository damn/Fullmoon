(ns components.widgets.skill-window
  (:require [core.components :as components]
            [core.context :as ctx]
            [gdx.scene2d.actor :refer [add-tooltip!]]
            [gdx.scene2d.ui :as ui]))

; TODO render text label free-skill-points
; (str "Free points: " (:entity/free-skill-points @player-entity))
(defn create [context]
  (ui/->window {:title "Skills"
                :id :skill-window
                :visible? false
                :cell-defaults {:pad 10}
                :rows [(for [id [:skills/projectile
                                 :skills/meditation
                                 :skills/spawn
                                 :skills/melee-attack]
                             :let [; get-property in callbacks if they get changed, this is part of context permanently
                                   button (ui/->image-button context ; TODO reuse actionbar button scale?
                                                             (:entity/image (ctx/property context id)) ; TODO here anyway taken
                                                             ; => should probably build this window @ game start
                                                             (fn [ctx]
                                                               (ctx/do! ctx (ctx/player-clicked-skillmenu ctx (ctx/property ctx id)))))]]
                         (do
                          (add-tooltip! button #(components/info-text (ctx/property % id) %)) ; TODO no player modifiers applied (see actionbar)
                          button))]
                :pack? true}))
