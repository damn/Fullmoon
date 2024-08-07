(ns context.game-widgets
  (:require [utils.core :as utils]
            [core.component :refer [defcomponent] :as component]
            [api.context :as ctx]
            [api.scene2d.actor :as actor]
            [api.scene2d.group :as group]
            [api.input.keys :as input.keys]
            [entity-state.player-item-on-cursor :refer [draw-item-on-cursor]]
            [context.player-message :refer [->player-message-actor]]
            [widgets.action-bar :as action-bar]
            [widgets.debug-window :as debug-window]
            [widgets.entity-info-window :as entity-info-window]
            [widgets.inventory :as inventory]
            [widgets.hp-mana-bars :refer [->hp-mana-bars]]))

; TODO same space/pad as action-bar (actually inventory cells too)
; => global setting use ?
(defn- ->action-bar-table [ctx]
  (ctx/->table ctx {:rows [[{:actor (action-bar/->build ctx)
                             :expand? true
                             :bottom? true
                             :left? true}]]
                    :id ::action-bar-table
                    :cell-defaults {:pad 2}
                    :fill-parent? true}))

(extend-type api.context.Context
  api.context/Actionbar
  (selected-skill [{{:keys [action-bar/button-group]} :context/game-widgets}]
    (when-let [skill-button (api.scene2d.ui.button-group/checked button-group)]
      (actor/id skill-button)))

  api.context/InventoryWindow
  (inventory-window [ctx]
    (get (:windows (ctx/get-stage ctx)) :inventory-window)))

(defn- ->windows [context widget-data]
  (ctx/->group context {:id :windows ; TODO namespaced keyword.
                        :actors [(debug-window/create context)
                                 (entity-info-window/create context)
                                 (inventory/->build context widget-data)]}))

(defn- ->item-on-cursor-actor [context]
  (ctx/->actor context {:draw draw-item-on-cursor}))

(defn- ->ui-actors [ctx widget-data]
  [(->action-bar-table     ctx)
   (->hp-mana-bars         ctx)
   (->windows              ctx widget-data)
   (->item-on-cursor-actor ctx)
   (->player-message-actor ctx)])

; cannot use get-stage as we are still in main menu
(defn- reset-stage-actors! [ctx widget-data]
  (let [stage (-> ctx
                  :context/screens
                  :screens
                  :screens/game
                  :stage)]
    (group/clear-children! stage)
    (doseq [actor (->ui-actors ctx widget-data)]
      (group/add-actor! stage actor))))

(defcomponent :context/game-widgets {}
  (component/create [_ ctx]
    (let [widget-data {:action-bar/button-group (action-bar/->button-group ctx)
                       :slot->background (inventory/->data ctx)}]
      (reset-stage-actors! ctx widget-data)
      widget-data)))

; TODO maybe here get-widget inventory/action-bar/ ?

; for now a function, see context.input reload bug
; otherwise keys in dev mode may be unbound because dependency order not reflected
; because bind-roots
(defn- hotkey->window-id [{:keys [context/config] :as ctx}]
  (merge
   {input.keys/i :inventory-window
    input.keys/e :entity-info-window}
   (when (utils/safe-get config :debug-window?)
     {input.keys/z :debug-window})))

(defn check-window-hotkeys [ctx]
  (doseq [[hotkey window-id] (hotkey->window-id ctx)
          :when (ctx/key-just-pressed? ctx hotkey)]
    (actor/toggle-visible! (get (:windows (ctx/get-stage ctx)) window-id))))

(defn close-windows? [context]
  (let [windows (group/children (:windows (ctx/get-stage context)))]
    (if (some actor/visible? windows)
      (do
       (run! #(actor/set-visible! % false) windows)
       true))))
