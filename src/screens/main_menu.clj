(ns screens.main-menu
  (:require [gdx.app :as app]
            [gdx.input :as input]
            [gdx.input.keys :as input.keys]
            [core.component :refer [defcomponent]]
            [utils.core :refer [safe-get]]
            [app :refer [current-context change-screen!]]
            [api.context :as ctx]
            [api.screen :as screen :refer [Screen]]
            ; just load here not @ resources because we don't build it yet.
            ; because ui widgets can only be created @ game screen is current screen
            [context.game :as game]
            [widgets.background-image :refer [->background-image]]
            mapgen.module-gen))

(defn- ->vampire-tmx [ctx]
  {:tiled-map (ctx/->tiled-map ctx "maps/vampire.tmx")
   :start-position [32 71]})

(defn- ->uf-caves [ctx]
  (mapgen.module-gen/uf-caves ctx {:world/map-size 250
                                   :world/spawn-rate 0.02}))

(defn- ->rand-module-world [ctx]
  (let [{:keys [tiled-map
                start-position]} (mapgen.module-gen/generate
                                  ctx
                                  (ctx/get-property ctx :worlds/first-level))]
    {:tiled-map tiled-map
     :start-position start-position}))

(defn- start-vampire! [ctx]
  (change-screen! :screens/game)
  (swap! current-context game/start-new-game (->vampire-tmx ctx)))

(defn- start-uf-caves! [ctx]
  (change-screen! :screens/game)
  (swap! current-context game/start-new-game (->uf-caves ctx)))

(defn- start-procedural! [ctx]
  (change-screen! :screens/game)
  (swap! current-context game/start-new-game (->rand-module-world ctx)))

(defn- map-editor!      [_ctx] (change-screen! :screens/map-editor))
(defn- property-editor! [_ctx] (change-screen! :screens/property-editor))

(defn- ->buttons [{:keys [context/config] :as ctx}]
  (ctx/->table
   ctx
   {:rows (remove nil?
                  [[(ctx/->text-button ctx "Start vampire.tmx" start-vampire!)]
                   [(ctx/->text-button ctx "start-uf-caves!" start-uf-caves!)]
                   [(ctx/->text-button ctx "Start procedural" start-procedural!)]
                   (when (safe-get config :map-editor?)
                     [(ctx/->text-button ctx "Map editor" map-editor!)])
                   (when (safe-get config :property-editor?)
                     [(ctx/->text-button ctx "Property editor" property-editor!)])
                   [(ctx/->text-button ctx "Exit" (fn [_ctx]
                                                    (app/exit)))]])
    :cell-defaults {:pad-bottom 25}
    :fill-parent? true}))

(defrecord SubScreen []
  Screen
  (show [_ ctx]
    (ctx/set-cursor! ctx :cursors/default))
  (hide [_ ctx])
  (render [_ ctx] ctx))

(defcomponent :screens/main-menu {}
  (screen/create [_ ctx]
    (ctx/->stage-screen ctx {:actors [(->background-image ctx)
                                      (->buttons ctx)
                                      (ctx/->actor ctx {:act (fn [_ctx]
                                                               (when (input/key-just-pressed? input.keys/escape)
                                                                 (app/exit)))})]
                             :sub-screen (->SubScreen)})))
