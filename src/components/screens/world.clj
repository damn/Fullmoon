(ns components.screens.world
  (:require [gdx.graphics.camera :as camera]
            [gdx.graphics.orthographic-camera :as orthographic-camera]
            [gdx.input :as input]
            [gdx.input.keys :as input.keys]
            [core.component :refer [defcomponent] :as component]
            [core.context :as ctx]
            [components.context.world :as world]
            (components.world [debug-render :as debug-render]
                              [widgets :as widgets])))

(defn- render-world! [ctx]
  (let [player-entity* (ctx/player-entity* ctx)]
    (camera/set-position! (ctx/world-camera ctx) (:position player-entity*))
    (components.world.render/render-map ctx (camera/position (ctx/world-camera ctx)))
    (ctx/render-world-view ctx
                           (fn [g]
                             (debug-render/before-entities ctx g)
                             (ctx/render-entities! ctx
                                                   g
                                                   (->> (world/active-entities ctx)
                                                        (map deref)
                                                        (filter :z-order)
                                                        (filter #(ctx/line-of-sight? ctx player-entity* %))))
                             (debug-render/after-entities ctx g)))))

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

(defcomponent ::sub-screen
  (component/exit [_ ctx]
    (ctx/set-cursor! ctx :cursors/default))

  (component/render-ctx [_ ctx]
    (render-world! ctx)
    (-> ctx
        world/game-loop
        check-key-input)))

(derive :screens/world :screens/stage-screen)
(defcomponent :screens/world
  (component/create [_ ctx]
    {:stage (ctx/->stage ctx [])
     :sub-screen [::sub-screen]}))
