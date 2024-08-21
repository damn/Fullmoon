(ns screens.world
  (:require [gdx.graphics.orthographic-camera :as orthographic-camera]
            [gdx.input :as input]
            [gdx.input.keys :as input.keys]
            [core.component :refer [defcomponent]]
            [api.context :as ctx]
            [api.screen :as screen :refer [Screen]]
            [context.world :as world]
            [world.widgets :as widgets]))

(defn- adjust-zoom [camera by] ; DRY map editor
  (orthographic-camera/set-zoom! camera (max 0.1 (+ (orthographic-camera/zoom camera) by))))

(def ^:private zoom-speed 0.05)

(defn- check-zoom-keys [ctx]
  (let [camera (ctx/world-camera ctx)]
    (when (input/key-pressed? input.keys/minus)  (adjust-zoom camera    zoom-speed))
    (when (input/key-pressed? input.keys/equals) (adjust-zoom camera (- zoom-speed)))))

; TODO move to actor/stage listeners ? then input processor used ....
(defn- check-key-input [ctx]
  (check-zoom-keys ctx)
  (widgets/check-window-hotkeys ctx)
  (cond (and (input/key-just-pressed? input.keys/escape)
             (not (widgets/close-windows? ctx)))
        (ctx/change-screen ctx :screens/options-menu)

        ; TODO not implementing StageSubScreen so NPE no screen/render!
        #_(input/key-just-pressed? input.keys/tab)
        #_(ctx/change-screen ctx :screens/minimap)

        :else
        ctx))

(defrecord SubScreen []
  Screen
  (show [_ _ctx])
  (hide [_ ctx]
    (ctx/set-cursor! ctx :cursors/default))
  (render [_ ctx]
    (world/render! ctx)
    (-> ctx
        world/game-loop
        check-key-input)))

(defcomponent :screens/world {}
  (screen/create [_ ctx]
    (ctx/->stage-screen ctx
                        {:actors []
                         :sub-screen (->SubScreen)})))
