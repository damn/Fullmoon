(ns components.world.widgets
  (:require [utils.core :as utils]
            [core.component :refer [defcomponent] :as component]
            [core.context :as ctx]
            [gdx.scene2d.actor :as actor]
            [gdx.scene2d.group :as group]
            [gdx.scene2d.ui :as ui]
            [components.entity-state.player-item-on-cursor :refer [draw-item-on-cursor]]
            [components.widgets.action-bar :as action-bar]
            [components.widgets.debug-window :as debug-window]
            [components.widgets.entity-info-window :as entity-info-window]
            [components.widgets.inventory :as inventory]
            [components.widgets.hp-mana-bars :refer [->hp-mana-bars]])
  (:import (com.badlogic.gdx Gdx Input$Keys)
           com.badlogic.gdx.scenes.scene2d.Stage
           com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup))

(extend-type core.context.Context
  core.context/Actionbar
  (selected-skill [ctx]
    (let [button-group (:action-bar (:world/widgets ctx))]
      (when-let [skill-button (.getChecked ^ButtonGroup button-group)]
        (actor/id skill-button))))

  core.context/InventoryWindow
  (inventory-window [ctx]
    (get (:windows (ctx/get-stage ctx)) :inventory-window)))

(defn- ->ui-actors [ctx widget-data]
  [(ui/->table {:rows [[{:actor (action-bar/->build)
                         :expand? true
                         :bottom? true}]]
                :id :action-bar-table
                :cell-defaults {:pad 2}
                :fill-parent? true})
   (->hp-mana-bars ctx)
   (ui/->group {:id :windows
                :actors [(debug-window/create ctx)
                         (entity-info-window/create ctx)
                         (inventory/->build ctx widget-data)]})
   (ui/->actor ctx {:draw draw-item-on-cursor})
   (component/create [:widgets/player-message] ctx)])

(defcomponent :world/widgets
  (component/create [_ ctx]
    (assert (= :screens/world (ctx/current-screen-key ctx)))
    (let [widget-data {:action-bar (action-bar/->button-group)
                       :slot->background (inventory/->data ctx)}
          stage (ctx/get-stage ctx)]
      (.clear stage)
      (run! #(.addActor stage %) (->ui-actors ctx widget-data))
      widget-data)))

(defn- hotkey->window-id [{:keys [context/config]}]
  (merge {Input$Keys/I :inventory-window
          Input$Keys/E :entity-info-window}
         (when (utils/safe-get config :debug-window?)
           {Input$Keys/Z :debug-window})))

(extend-type core.context.Context
  core.context/IngameWindows
  (check-window-hotkeys [ctx]
    (doseq [[hotkey window-id] (hotkey->window-id ctx)
            :when (.isKeyJustPressed Gdx/input hotkey)]
      (actor/toggle-visible! (get (:windows (ctx/get-stage ctx)) window-id))))

  (close-windows? [context]
    (let [windows (group/children (:windows (ctx/get-stage context)))]
      (if (some actor/visible? windows)
        (do
         (run! #(actor/set-visible! % false) windows)
         true)))))
