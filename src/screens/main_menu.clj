(ns screens.main-menu
  (:require [gdx.app :as app]
            [gdx.input :as input]
            [gdx.input.keys :as input.keys]
            [core.component :refer [defcomponent]]
            [utils.core :refer [safe-get]]
            [api.context :as ctx]
            [api.screen :as screen :refer [Screen]]
            ; just load here not @ resources because we don't build it yet.
            ; because ui widgets can only be created @ game screen is current screen
            [context.world :as world]
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

(defn- start-game! [lvl-fn]
  (fn [ctx]
    (-> ctx
        (ctx/change-screen :screens/world)
        (world/start-new-game (lvl-fn ctx)))))

(defn- ->buttons [{:keys [context/config] :as ctx}]
  (ctx/->table
   ctx
   {:rows (remove nil?
                  [[(ctx/->text-button ctx "Start vampire.tmx" (start-game! ->vampire-tmx))]
                   [(ctx/->text-button ctx "start-uf-caves!"   (start-game! ->uf-caves))]
                   [(ctx/->text-button ctx "Start procedural"  (start-game! ->rand-module-world))]
                   (when (safe-get config :map-editor?)
                     [(ctx/->text-button ctx "Map editor" #(ctx/change-screen % :screens/map-editor))])
                   (when (safe-get config :property-editor?)
                     [(ctx/->text-button ctx "Property editor" #(ctx/change-screen % :screens/property-editor))])
                   [(ctx/->text-button ctx "Exit" (fn [ctx] (app/exit) ctx))]])
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
