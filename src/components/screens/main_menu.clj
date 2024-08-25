(ns components.screens.main-menu
  (:require [gdx.app :as app]
            [gdx.input :as input]
            [gdx.input.keys :as input.keys]
            [utils.core :refer [safe-get]]
            [core.component :refer [defcomponent] :as component]
            [core.context :as ctx]))

(defn- start-game! [world-id]
  (fn [ctx]
    (-> ctx
        (ctx/change-screen :screens/world)
        (ctx/start-new-game (ctx/->world ctx world-id)))))

(comment
 (ctx/all-properties context :properties/world)

 )

(defn- ->buttons [{:keys [context/config] :as ctx}]
  (ctx/->table
   ctx
   {:rows (remove nil?
                  [[(ctx/->text-button ctx "Start vampire.tmx" (start-game! :worlds/vampire))]
                   [(ctx/->text-button ctx "start-uf-caves!"   (start-game! :worlds/uf-caves))]
                   [(ctx/->text-button ctx "Start procedural"  (start-game! :worlds/modules))]
                   (when (safe-get config :map-editor?)
                     [(ctx/->text-button ctx "Map editor" #(ctx/change-screen % :screens/map-editor))])
                   (when (safe-get config :property-editor?)
                     [(ctx/->text-button ctx "Property editor" #(ctx/change-screen % :screens/property-editor))])
                   [(ctx/->text-button ctx "Exit" (fn [ctx] (app/exit) ctx))]])
    :cell-defaults {:pad-bottom 25}
    :fill-parent? true}))


(defcomponent ::sub-screen
  (component/enter [_ ctx]
    (ctx/set-cursor! ctx :cursors/default)))

(defn- ->actors [ctx]
  [(ctx/->background-image ctx)
   (->buttons ctx)
   (ctx/->actor ctx {:act (fn [_ctx]
                            (when (input/key-just-pressed? input.keys/escape)
                              (app/exit)))})])

(derive :screens/main-menu :screens/stage-screen)
(defcomponent :screens/main-menu
  (component/create [[k _] ctx]
    {:sub-screen [::sub-screen]
     :stage (ctx/->stage ctx (->actors ctx))}))
