(ns screens.main-menu
  (:require [core.component :as component]
            [utils.core :refer [safe-get tile->middle]]
            [app.state :refer [current-context change-screen!]]
            [api.context :as ctx]
            [api.input.keys :as input.keys]
            [api.screen :as screen]
            mapgen.module-gen))

(defn- ->vampire-tmx [ctx]
  {:tiled-map (ctx/->tiled-map ctx "maps/vampire.tmx")
   :start-position (tile->middle [32 71])})

(defn- start-vampire! [ctx]
  (swap! current-context ctx/start-new-game (->vampire-tmx ctx))
  (change-screen! :screens/game))

(defn- ->rand-module-world [ctx]
  (let [{:keys [tiled-map
                start-position]} (mapgen.module-gen/generate
                                  ctx
                                  (ctx/get-property ctx :worlds/first-level))]
    {:tiled-map tiled-map
     :start-position (tile->middle start-position)}))

(defn- start-procedural! [ctx]
  (swap! current-context ctx/start-new-game (->rand-module-world ctx))
  (change-screen! :screens/game))

(defn- map-editor!      [_ctx] (change-screen! :screens/map-editor))
(defn- property-editor! [_ctx] (change-screen! :screens/property-editor))

(defn- ->buttons [{:keys [context/config] :as ctx}]
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

(component/def :screens/main-menu {}
  _
  (screen/create [_ ctx]
    (ctx/->stage-screen ctx {:actors [(ctx/->background-image ctx)
                                      (->buttons ctx)
                                      (ctx/->actor ctx {:act (fn [ctx]
                                                               (when (ctx/key-just-pressed? ctx input.keys/escape)
                                                                 (ctx/exit-app ctx)))})]})))
