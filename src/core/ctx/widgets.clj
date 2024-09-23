(ns core.ctx.widgets
  (:require [core.utils.core :as utils]
            [core.component :refer [defcomponent] :as component]
            [core.ui.actor :as actor]
            [core.ui.group :as group]
            [core.ui :as ui]
            [core.entity.state.player-item-on-cursor :refer [draw-item-on-cursor]]
            [core.screens.stage :as stage]
            [core.widgets.action-bar :as action-bar]
            [core.widgets.debug-window :as debug-window]
            [core.widgets.entity-info-window :as entity-info-window]
            [core.widgets.hp-mana-bars :refer [->hp-mana-bars]]
            [core.widgets.inventory :as inventory])
  (:import (com.badlogic.gdx Gdx Input$Keys)
           com.badlogic.gdx.scenes.scene2d.Stage
           com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup))

(defn selected-skill [ctx]
  (let [button-group (:action-bar (:context/widgets ctx))]
    (when-let [skill-button (.getChecked ^ButtonGroup button-group)]
      (actor/id skill-button))))

(defn inventory-window [ctx]
  (get (:windows (stage/get ctx)) :inventory-window))

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

(defcomponent :context/widgets
  (component/create [_ ctx]
    (let [widget-data {:action-bar (action-bar/->button-group)
                       :slot->background (inventory/->data ctx)}
          stage (stage/get ctx)]
      (.clear stage)
      (run! #(.addActor stage %) (->ui-actors ctx widget-data))
      widget-data)))

(defn- hotkey->window-id [{:keys [context/config]}]
  (merge {Input$Keys/I :inventory-window
          Input$Keys/E :entity-info-window}
         (when (utils/safe-get config :debug-window?)
           {Input$Keys/Z :debug-window})))

(defn check-window-hotkeys [ctx]
  (doseq [[hotkey window-id] (hotkey->window-id ctx)
          :when (.isKeyJustPressed Gdx/input hotkey)]
    (actor/toggle-visible! (get (:windows (stage/get ctx)) window-id))))

(defn close-windows? [context]
  (let [windows (group/children (:windows (stage/get context)))]
    (if (some actor/visible? windows)
      (do
       (run! #(actor/set-visible! % false) windows)
       true))))
