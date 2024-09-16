(ns components.graphics.views
  (:require [gdx.utils.viewport.viewport :as viewport]
            [core.context :as ctx]
            [core.graphics :as g])
  (:import (com.badlogic.gdx Gdx OrthographicCamera)
           [com.badlogic.gdx.math MathUtils Vector2]
           com.badlogic.gdx.utils.viewport.FitViewport))

(defn- ->gui-view [{:keys [world-width world-height]}]
  {:unit-scale 1
   :viewport (FitViewport. world-width
                           world-height
                           (OrthographicCamera.))})

(defn- ->world-view [{:keys [world-width world-height tile-size]}]
  (let [unit-scale (/ tile-size)]
    {:unit-scale (float unit-scale)
     :viewport (let [world-width  (* world-width  unit-scale)
                     world-height (* world-height unit-scale)
                     camera (OrthographicCamera.)
                     y-down? false]
                 (.setToOrtho camera y-down? world-width world-height)
                 (FitViewport. world-width world-height camera))}))

(defn ->build [{:keys [gui-view world-view]}]
  {:gui-view (->gui-view gui-view)
   :world-view (->world-view world-view)})

(extend-type core.graphics.Graphics
  core.graphics/WorldView
  (world-unit-scale [{:keys [world-view]}]
    (:unit-scale world-view))

  (pixels->world-units [g pixels]
    (* (int pixels) (g/world-unit-scale g))))

(defn- gui-viewport   [g] (-> g :gui-view   :viewport))
(defn- world-viewport [g] (-> g :world-view :viewport))

(defn- clamp [value min max]
  (MathUtils/clamp (float value) (float min) (float max)))

; touch coordinates are y-down, while screen coordinates are y-up
; so the clamping of y is reverse, but as black bars are equal it does not matter
(defn- unproject-mouse-posi [viewport]
  (let [mouse-x (clamp (.getX Gdx/input)
                       (viewport/left-gutter-width viewport)
                       (viewport/right-gutter-x viewport))
        mouse-y (clamp (.getY Gdx/input)
                       (viewport/top-gutter-height viewport)
                       (viewport/top-gutter-y viewport))
        coords (viewport/unproject viewport (Vector2. mouse-x mouse-y))]
    [(.x coords) (.y coords)]))

(defn- gui-mouse-position [g]
  ; TODO mapv int needed?
  (mapv int (unproject-mouse-posi (gui-viewport g))))

(defn- world-mouse-position [g]
  ; TODO clamping only works for gui-viewport ? check. comment if true
  ; TODO ? "Can be negative coordinates, undefined cells."
  (unproject-mouse-posi (world-viewport g)))

(defn- gr [ctx] (:context/graphics ctx))

(extend-type core.context.Context
  core.context/Views
  (gui-mouse-position    [ctx] (gui-mouse-position   (gr ctx)))
  (world-mouse-position  [ctx] (world-mouse-position (gr ctx)))
  (gui-viewport-width    [ctx] (viewport/world-width  (gui-viewport   (gr ctx))))
  (gui-viewport-height   [ctx] (viewport/world-height (gui-viewport   (gr ctx))))
  (world-camera          [ctx] (viewport/camera       (world-viewport (gr ctx))))
  (world-viewport-width  [ctx] (viewport/world-width  (world-viewport (gr ctx))))
  (world-viewport-height [ctx] (viewport/world-height (world-viewport (gr ctx))))

  (update-viewports [{g :context/graphics} w h]
    (viewport/update (gui-viewport g) w h true)
    ; Do not center the camera on world-viewport. We set the position there manually.
    (viewport/update (world-viewport g) w h false)))
