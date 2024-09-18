(ns components.screens.world
  (:require [gdx.graphics.camera :as camera]
            [core.component :refer [defcomponent] :as component]
            [core.context :as ctx]
            [core.screen :as screen]
            [components.context.world :as world])
  (:import (com.badlogic.gdx Gdx Input$Keys)))

(defn- render-world! [ctx]
  (camera/set-position! (ctx/world-camera ctx) (:position (ctx/player-entity* ctx)))
  (ctx/render-map ctx (camera/position (ctx/world-camera ctx)))
  (ctx/render-world-view ctx
                         (fn [g]
                           (ctx/debug-render-before-entities ctx g)
                           (ctx/render-entities! ctx
                                                 g
                                                 (->> (ctx/active-entities ctx)
                                                      (map deref)))
                           (ctx/debug-render-after-entities ctx g))))

(defn- adjust-zoom [camera by] ; DRY map editor
  (camera/set-zoom! camera (max 0.1 (+ (camera/zoom camera) by))))

(def ^:private zoom-speed 0.05)

(defn- check-zoom-keys [ctx]
  (let [camera (ctx/world-camera ctx)]
    (when (.isKeyPressed Gdx/input Input$Keys/MINUS)  (adjust-zoom camera    zoom-speed))
    (when (.isKeyPressed Gdx/input Input$Keys/EQUALS) (adjust-zoom camera (- zoom-speed)))))

; TODO move to actor/stage listeners ? then input processor used ....
(defn- check-key-input [ctx]
  (check-zoom-keys ctx)
  (ctx/check-window-hotkeys ctx)
  (cond (and (.isKeyJustPressed Gdx/input Input$Keys/ESCAPE)
             (not (ctx/close-windows? ctx)))
        (ctx/change-screen ctx :screens/options-menu)

        ; TODO not implementing StageSubScreen so NPE no screen/render!
        #_(.isKeyJustPressed Gdx/input Input$Keys/TAB)
        #_(ctx/change-screen ctx :screens/minimap)

        :else
        ctx))

(defcomponent ::sub-screen
  (component/exit [_ ctx]
    (ctx/set-cursor! ctx :cursors/default))

  (screen/render [_ ctx]
    (render-world! ctx)
    (-> ctx
        world/game-loop
        check-key-input)))

(derive :screens/world :screens/stage-screen)
(defcomponent :screens/world
  (component/create [_ ctx]
    {:stage (ctx/->stage ctx [])
     :sub-screen [::sub-screen]}))
