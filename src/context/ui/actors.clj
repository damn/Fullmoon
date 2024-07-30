(ns context.ui.actors
  (:require gdl.libgdx.context.image-drawer-creator
            [gdl.app :refer [change-screen!]]
            [api.context :refer [->actor ->table ->group ->text-button get-stage ->action-bar id->window]]
            [gdl.scene2d.actor :refer [toggle-visible! add-tooltip!]]
            [gdl.scene2d.group :refer [children]]
            [utils.core :refer [safe-get]]
            [context.inventory :as inventory]
            [context.player-message :refer [->player-message-actor]]
            [context.ui.hp-mana-bars :refer [->hp-mana-bars]]
            [context.ui.debug-window :as debug-window]
            [context.ui.help-window :as help-window]
            [context.ui.entity-info-window :as entity-info-window]
            [context.ui.skill-window :as skill-window]
            [state.player-item-on-cursor :refer [draw-item-on-cursor]]))

(defn- ->item-on-cursor-actor [context]
  (->actor context {:draw draw-item-on-cursor}))

(extend-type api.context.Context
  api.context/Windows
  (windows [ctx]
    (children (:windows (get-stage ctx))))

  (id->window [ctx window-id]
    (get (:windows (get-stage ctx)) window-id)))

; TODO reused from properties, move gdl.
(defn ->sprite [ctx sprite-idx]
  (gdl.libgdx.context.image-drawer-creator/map->Image
   (api.context/get-sprite ctx
                           {:file "ui/uf_interface.png"
                            :tilew 24
                            :tileh 24}
                           sprite-idx)))

(defn- ->option-button-cell [ctx sprite-idx tooltip-text f]
  {:actor (let [button  (api.context/->image-button ctx
                                                    (->sprite ctx sprite-idx)
                                                    f
                                                    {:dimensions [32 32]})]
            (add-tooltip! button tooltip-text)
            button)
   :bottom? true})

; TODO show hotkeys - move controls/hotkeys/help window together
; I hotkey for inventory doesnt work production

; TODO change-screen here ok?
(defn- ->buttons [{:keys [context/config] :as ctx}]
  (let [debug-windows? (safe-get config :debug-windows?)
        toggle! (fn [id]
                  (fn [ctx] (toggle-visible! (id->window ctx id))))]
    (for [[sprite-idx tooltip-text f] [[[7 1]   "Options" (fn [_] (change-screen! :screens/options-menu))]
                                       [[4 1]   "Minimap" (fn [_] (change-screen! :screens/minimap))]
                                       [[10 1]  "Inventory"   (toggle! :inventory-window)]
                                       [[6 1]   "Skills"      (toggle! :skill-window)]
                                       [[9 0]   "Help"        (toggle! :help-window)]
                                       (when debug-windows?
                                         [[8 0] "Entity Info" (toggle! :entity-info-window)])
                                       (when debug-windows?
                                         [[3 0] "Debug"       (toggle! :debug-window)])]
          :when sprite-idx]
      (->option-button-cell ctx sprite-idx tooltip-text f))))

; TODO same space/pad as action-bar (actually inventory cells too)
; => global setting use ?
(defn- ->base-table [ctx]
  (->table ctx {:rows [(cons {:actor (->action-bar ctx)
                              :expand? true
                              :bottom? true
                              :left? true}
                             (remove nil? (->buttons ctx)))]
                :id ::main-table
                :cell-defaults {:pad 2}
                :fill-parent? true}))

(defn- ->windows [context]
  (->group context {:id :windows
                    :actors [(debug-window/create context)
                             (help-window/create context)
                             (entity-info-window/create context)
                             (inventory/->inventory-window context)
                             (skill-window/create context)]}))

(defn ->ui-actors [ctx]
  [(->base-table           ctx)
   (->hp-mana-bars         ctx)
   (->windows              ctx)
   (->item-on-cursor-actor ctx)
   (->player-message-actor ctx)])
