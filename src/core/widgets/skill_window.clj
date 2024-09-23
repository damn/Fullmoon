(ns core.widgets.skill-window
  (:require [core.component :as component]
            [core.ctx.property :as property]
            [core.entity.player :as player]
            [core.effect :as effect]
            [core.ui.actor :refer [add-tooltip!]]
            [core.ctx.ui :as ui]))

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
                                                             (:entity/image (property/build context id)) ; TODO here anyway taken
                                                             ; => should probably build this window @ game start
                                                             (fn [ctx]
                                                               (effect/do ctx (player/clicked-skillmenu ctx (property/build ctx id)))))]]
                         (do
                          (add-tooltip! button #(component/->text (property/build % id) %)) ; TODO no player modifiers applied (see actionbar)
                          button))]
                :pack? true}))
