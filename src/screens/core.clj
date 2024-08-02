(ns screens.core
  (:require [core.component :refer [defcomponent]]
            [utils.core :refer [safe-get tile->middle]]
            [app.state :refer [current-context change-screen!]]
            [api.context :as ctx]
            [api.input.keys :as input.keys]
            [api.screen :as screen]
            mapgen.module-gen))

#_(defn- ->buttons [{:keys [context/config] :as ctx}]
  (ctx/->table
   ctx
   {:rows (remove nil?
                  [[(ctx/->text-button ctx "Start vampire.tmx" start-vampire!)]
                   [(ctx/->text-button ctx "Start procedural" start-procedural!)]
                   (when (safe-get config :map-editor?)
                     [(ctx/->text-button ctx "Map editor" map-editor!)])
                   (when (safe-get config :property-editor?)
                     [(ctx/->text-button ctx "Property editor" property-editor!)])
                   [(ctx/->text-button ctx "Exit" ctx/exit-app)]])
    :cell-defaults {:pad-bottom 25}
    :fill-parent? true}))


(import 'com.kotcrab.vis.ui.widget.MenuBar)
(import 'com.kotcrab.vis.ui.widget.Menu)
(import 'com.kotcrab.vis.ui.widget.MenuItem)

(defn- ->menu-bar []
  (let [menu-bar (MenuBar.)
        app-menu (Menu. "App")]
    (.addItem app-menu (MenuItem. "New app"))
    (.addItem app-menu (MenuItem. "Load app"))
    (.addMenu menu-bar app-menu)
    (.addMenu menu-bar (Menu. "Properties"))
    (def mybar menu-bar)
    menu-bar))

(defn- ->menu [ctx]
  (ctx/->table ctx {:rows [[{:actor (.getTable (->menu-bar))
                             ;:left? true
                             :expand-x? true
                             :fill-x? true
                             :colspan 1}]
                           [{:actor (ctx/->label ctx "")
                             :expand? true
                             :fill-x? true
                             :fill-y? true}]]
                    :fill-parent? true}))

(defcomponent :screens/core {}
  (screen/create [_ ctx]
    (ctx/->stage-screen ctx {:actors [(ctx/->background-image ctx)
                                      (->menu ctx)
                                      ;(->buttons ctx)
                                      (ctx/->actor ctx {:act (fn [ctx]
                                                               (when (ctx/key-just-pressed? ctx input.keys/escape)
                                                                 (ctx/exit-app ctx)))})]})))
