(ns components.world.widgets
  (:require [gdx.input :as input]
            [gdx.input.keys :as input.keys]
            [gdx.scene2d.stage :as stage]
            [utils.core :as utils]
            [core.component :refer [defcomponent] :as component]
            [core.context :as ctx]
            [core.scene2d.actor :as actor]
            [core.scene2d.group :as group]
            core.scene2d.ui.button-group
            [components.entity-state.player-item-on-cursor :refer [draw-item-on-cursor]]
            [components.widgets.action-bar :as action-bar]
            [components.widgets.debug-window :as debug-window]
            [components.widgets.entity-info-window :as entity-info-window]
            [components.widgets.inventory :as inventory]
            [components.widgets.hp-mana-bars :refer [->hp-mana-bars]]))

(extend-type core.context.Context
  core.context/Actionbar
  (selected-skill [ctx]
    (let [button-group (:action-bar (:world/widgets ctx))]
      (when-let [skill-button (core.scene2d.ui.button-group/checked button-group)]
        (actor/id skill-button))))

  core.context/InventoryWindow
  (inventory-window [ctx]
    (get (:windows (ctx/get-stage ctx)) :inventory-window)))

(defn- ->ui-actors [ctx widget-data]
  [(ctx/->table ctx {:rows [[{:actor (action-bar/->build ctx)
                              :expand? true
                              :bottom? true}]]
                     :id :action-bar-table
                     :cell-defaults {:pad 2}
                     :fill-parent? true})
   (->hp-mana-bars ctx)
   (ctx/->group ctx {:id :windows
                     :actors [(debug-window/create ctx)
                              (entity-info-window/create ctx)
                              (inventory/->build ctx widget-data)]})
   (ctx/->actor ctx {:draw draw-item-on-cursor})
   (component/create [:widgets/player-message] ctx)])

(defn- reset-stage-actors! [ctx widget-data]
  (assert (= :screens/world (ctx/current-screen-key ctx)))
  (doto (ctx/get-stage ctx)
    stage/clear!
    (stage/add-actors! (->ui-actors ctx widget-data))))

(defcomponent :world/widgets
  (component/create [_ ctx]
    (let [widget-data {:action-bar (action-bar/->button-group ctx)
                       :slot->background (inventory/->data ctx)}]
      (reset-stage-actors! ctx widget-data)
      widget-data)))

(defn- hotkey->window-id [{:keys [context/config] :as ctx}]
  (merge
   {input.keys/i :inventory-window
    input.keys/e :entity-info-window}
   (when (utils/safe-get config :debug-window?)
     {input.keys/z :debug-window})))

(extend-type core.context.Context
  core.context/IngameWindows
  (check-window-hotkeys [ctx]
    (doseq [[hotkey window-id] (hotkey->window-id ctx)
            :when (input/key-just-pressed? hotkey)]
      (actor/toggle-visible! (get (:windows (ctx/get-stage ctx)) window-id))))

  (close-windows? [context]
    (let [windows (group/children (:windows (ctx/get-stage context)))]
      (if (some actor/visible? windows)
        (do
         (run! #(actor/set-visible! % false) windows)
         true)))))
