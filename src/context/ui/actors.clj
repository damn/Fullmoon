(ns context.ui.actors
  (:require [api.context :refer [->actor ->table ->group ->text-button get-stage ->action-bar]]
            [api.scene2d.group :refer [children]]
            [utils.core :refer [safe-get]]
            [context.inventory :as inventory]
            [context.player-message :refer [->player-message-actor]]
            [context.ui.hp-mana-bars :refer [->hp-mana-bars]]
            [context.ui.debug-window :as debug-window]
            [context.ui.entity-info-window :as entity-info-window]
            [entity.state.player-item-on-cursor :refer [draw-item-on-cursor]]))

(defn- ->item-on-cursor-actor [context]
  (->actor context {:draw draw-item-on-cursor}))

(extend-type api.context.Context
  api.context/Windows
  (windows [ctx]
    (children (:windows (get-stage ctx))))

  (get-window [ctx window-id]
    (get (:windows (get-stage ctx)) window-id)))

; TODO same space/pad as action-bar (actually inventory cells too)
; => global setting use ?
(defn- ->base-table [ctx]
  (->table ctx {:rows [[{:actor (->action-bar ctx)
                         :expand? true
                         :bottom? true
                         :left? true}]]
                :id ::main-table
                :cell-defaults {:pad 2}
                :fill-parent? true}))

(defn- ->windows [context]
  (->group context {:id :windows
                    :actors [(debug-window/create context)
                             (entity-info-window/create context)
                             (inventory/->inventory-window context)]}))

(defn ->ui-actors [ctx]
  [(->base-table           ctx)
   (->hp-mana-bars         ctx)
   (->windows              ctx)
   (->item-on-cursor-actor ctx)
   (->player-message-actor ctx)])
