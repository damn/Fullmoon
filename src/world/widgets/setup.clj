(ns world.widgets.setup
  (:require [clojure.gdx.ui :as ui]
            [clojure.gdx.ui.stage :as stage]
            [clojure.gdx.ui.stage-screen :refer [stage-get]]
            [core.widgets.debug-window :as debug-window]
            [core.widgets.entity-info-window :as entity-info-window]
            [core.widgets.hp-mana :as hp-mana-bars]
            [core.widgets.player-message :as player-message]
            [world.core :as world]
            world.creature.states
            [world.entity.inventory :refer [->inventory-window ->inventory-window-data]]
            [world.entity.skills :refer [->action-bar ->action-bar-button-group]]))

(defn- actors [widget-data]
  [(ui/table {:rows [[{:actor (->action-bar)
                       :expand? true
                       :bottom? true}]]
              :id :action-bar-table
              :cell-defaults {:pad 2}
              :fill-parent? true})
   (hp-mana-bars/create)
   (ui/group {:id :windows
              :actors [(debug-window/create)
                       (entity-info-window/create)
                       (->inventory-window widget-data)]})
   (ui/actor {:draw world.creature.states/draw-item-on-cursor})
   (player-message/create)])

(defn- data []
  (let [widget-data {:action-bar (->action-bar-button-group)
                     :slot->background (->inventory-window-data)}
        stage (stage-get)]
    (stage/clear! stage)
    (run! #(stage/add! stage %) (actors widget-data))
    widget-data))

(defn init! []
  (.bindRoot #'world/widgets (data)))
