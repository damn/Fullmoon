(ns context.game-widgets
  (:require [core.component :refer [defcomponent] :as component]
            [api.context :as ctx]
            [api.scene2d.group :as group]
            [entity-state.player-item-on-cursor :refer [draw-item-on-cursor]]
            [context.player-message :refer [->player-message-actor]]
            [widgets.debug-window :as debug-window]
            [widgets.entity-info-window :as entity-info-window]
            [widgets.hp-mana-bars :refer [->hp-mana-bars]]
            ))

(defn- ->item-on-cursor-actor [context]
  (ctx/->actor context {:draw draw-item-on-cursor}))

; TODO same space/pad as action-bar (actually inventory cells too)
; => global setting use ?
(defn- ->action-bar-table [ctx]
  (ctx/->table ctx {:rows [[{:actor (ctx/->action-bar ctx)
                             :expand? true
                             :bottom? true
                             :left? true}]]
                    :cell-defaults {:pad 2}
                    :fill-parent? true}))

(defn- ->windows [context]
  (ctx/->group context {:id :windows
                        :actors [(debug-window/create context)
                                 (entity-info-window/create context)
                                 (ctx/inventory-window context)]}))

(defn- ->ui-actors [ctx]
  [(->action-bar-table     ctx)
   (->hp-mana-bars         ctx)
   (->windows              ctx)
   (->item-on-cursor-actor ctx)
   (->player-message-actor ctx)])

; cannot use get-stage as we are still in main menu
(defn- reset-stage-actors! [ctx]
  (let [stage (-> ctx
                  :context/screens
                  :screens
                  :screens/game
                  :stage)]
    (group/clear-children! stage)
    (doseq [actor (->ui-actors ctx)]
      (group/add-actor! stage actor))
    stage))

(defcomponent :context/game-widgets {}
  (component/create [_ ctx]
    (reset-stage-actors! ctx)))

; TODO maybe here get-widget inventory/action-bar/ ?
