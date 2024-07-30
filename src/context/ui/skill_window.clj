(ns context.ui.skill-window
  (:require [gdl.context :refer [->window ->image-button]]
            [gdl.scene2d.actor :refer [add-tooltip!]]
            [cdq.api.context :refer [get-property player-tooltip-text transact-all!]]
            [cdq.api.entity :as entity]
            [cdq.api.state :as state]))

(defn- clicked-skill [{:keys [context/player-entity] :as ctx} id]
  (let [entity* @player-entity]
    (state/clicked-skillmenu-skill (entity/state-obj entity*)
                                   entity*
                                   (get-property ctx id))))

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
                                button (->image-button context
                                                       (:property/image (get-property context id)) ; TODO here anyway taken
                                                       ; => should probably build this window @ game start
                                                       (fn [ctx]
                                                         (transact-all! ctx (clicked-skill ctx id))))]]
                      (do
                       (add-tooltip! button #(player-tooltip-text % (get-property % id)))
                       button))]
             :pack? true}))
