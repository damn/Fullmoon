(ns ^:no-doc core.screens.main-menu
  (:require [core.utils.core :refer [safe-get]]
            [core.ctx :refer :all]
            [core.graphics.cursors :as cursors]
            [core.screen :as screen]
            [core.ctx.screens :as screens]
            [core.screens.stage :as stage]
            [core.ctx.property :as property]
            core.world.ctx
            [core.widgets.background-image :refer [->background-image]]
            [core.property.types.world :as level-generator]
            [core.ctx.ui :as ui])
  (:import com.badlogic.gdx.Gdx
           com.badlogic.gdx.Input$Keys))

(defn- start-game! [world-id]
  (fn [ctx]
    (-> ctx
        (screens/change-screen :screens/world)
        (core.world.ctx/start-new-game (level-generator/->world ctx world-id)))))

(defn- ->buttons [{:keys [context/config] :as ctx}]
  (ui/->table {:rows (remove nil? (concat
                                   (for [{:keys [property/id]} (property/all-properties ctx :properties/worlds)]
                                     [(ui/->text-button (str "Start " id) (start-game! id))])
                                   [(when (safe-get config :map-editor?)
                                      [(ui/->text-button "Map editor" #(screens/change-screen % :screens/map-editor))])
                                    (when (safe-get config :property-editor?)
                                      [(ui/->text-button "Property editor" #(screens/change-screen % :screens/property-editor))])
                                    [(ui/->text-button "Exit" (fn [ctx] (.exit Gdx/app) ctx))]]))
               :cell-defaults {:pad-bottom 25}
               :fill-parent? true}))


(defcomponent ::sub-screen
  (screen/enter [_ ctx]
    (cursors/set-cursor! ctx :cursors/default)))

(defn- ->actors [ctx]
  [(->background-image ctx)
   (->buttons ctx)
   (ui/->actor {:act (fn [_ctx]
                       (when (.isKeyJustPressed Gdx/input Input$Keys/ESCAPE)
                         (.exit Gdx/app)))})])

(derive :screens/main-menu :screens/stage)
(defcomponent :screens/main-menu
  (->mk [[k _] ctx]
    {:sub-screen [::sub-screen]
     :stage (stage/create ctx (->actors ctx))}))
