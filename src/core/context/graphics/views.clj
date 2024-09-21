(ns core.context.graphics.views
  (:require [core.context :as ctx]
            [core.g :as g])
  (:import com.badlogic.gdx.Gdx
           com.badlogic.gdx.graphics.OrthographicCamera
           [com.badlogic.gdx.math MathUtils Vector2]
           (com.badlogic.gdx.utils.viewport Viewport FitViewport)))

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

(extend-type core.g.Graphics
  core.g/WorldView
  (world-unit-scale [{:keys [world-view]}]
    (:unit-scale world-view))

  (pixels->world-units [g pixels]
    (* (int pixels) (g/world-unit-scale g))))

(defn gui-viewport   ^Viewport [g] (-> g :gui-view   :viewport))
(defn world-viewport ^Viewport [g] (-> g :world-view :viewport))

(defn- clamp [value min max]
  (MathUtils/clamp (float value) (float min) (float max)))

; touch coordinates are y-down, while screen coordinates are y-up
; so the clamping of y is reverse, but as black bars are equal it does not matter
(defn- unproject-mouse-posi [^Viewport viewport]
  (let [mouse-x (clamp (.getX Gdx/input)
                       (.getLeftGutterWidth viewport)
                       (.getRightGutterX viewport))
        mouse-y (clamp (.getY Gdx/input)
                       (.getTopGutterHeight viewport)
                       (.getTopGutterY viewport))
        coords (.unproject viewport (Vector2. mouse-x mouse-y))]
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
  (gui-viewport-width    [ctx] (.getWorldWidth  (gui-viewport   (gr ctx))))
  (gui-viewport-height   [ctx] (.getWorldHeight (gui-viewport   (gr ctx))))
  (world-camera          [ctx] (.getCamera      (world-viewport (gr ctx))))
  (world-viewport-width  [ctx] (.getWorldWidth  (world-viewport (gr ctx))))
  (world-viewport-height [ctx] (.getWorldHeight (world-viewport (gr ctx)))))
