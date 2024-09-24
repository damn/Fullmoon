(ns ^:no-doc core.widgets.skill-window
  (:require [core.ctx :refer :all]
            [core.entity.player :as player]
            [core.ui :as ui]))

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
                                   button (ui/->image-button ; TODO reuse actionbar button scale?
                                                             (:entity/image (build-property context id)) ; TODO here anyway taken
                                                             ; => should probably build this window @ game start
                                                             (fn [ctx]
                                                               (effect! ctx (player/clicked-skillmenu ctx (build-property ctx id)))))]]
                         (do
                          (ui/add-tooltip! button #(->info-text (build-property % id) %)) ; TODO no player modifiers applied (see actionbar)
                          button))]
                :pack? true}))
