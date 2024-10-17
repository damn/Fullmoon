(ns app.screens.main
  (:require [app.screens.world :as world]
            [component.db :as db]
            [gdx.app :as app]
            [gdx.graphics :as g]
            [gdx.input :refer [key-just-pressed?]]
            [gdx.screen :as screen]
            [gdx.ui :as ui]
            [gdx.ui.stage-screen :as stage-screen]
            [utils.core :refer [dev-mode?]]))

(defn- ->buttons []
  (ui/table {:rows (remove nil? (concat
                                 (for [{:keys [property/id]} (db/all :properties/worlds)]
                                   [(ui/text-button (str "Start " id) (world/start-game-fn id))])
                                 [(when dev-mode?
                                    [(ui/text-button "Map editor" #(screen/change! :screens/map-editor))])
                                  (when dev-mode?
                                    [(ui/text-button "Property editor" #(screen/change! :screens/property-editor))])
                                  [(ui/text-button "Exit" app/exit!)]]))
             :cell-defaults {:pad-bottom 25}
             :fill-parent? true}))

(deftype MainMenuScreen []
  screen/Screen
  (screen/enter! [_]
    (g/set-cursor! :cursors/default))
  (screen/exit! [_])
  (screen/render! [_])
  (screen/dispose! [_]))

(defn create [->background-image]
  [:screens/main-menu
   (stage-screen/create :actors
                        [(->background-image)
                         (->buttons)
                         (ui/actor {:act (fn []
                                           (when (key-just-pressed? :keys/escape)
                                             (app/exit!)))})]
                        :screen (->MainMenuScreen))])
