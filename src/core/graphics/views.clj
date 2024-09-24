(ns core.graphics.views
  (:require [core.graphics :as g])
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

(defn ^:no-doc ->build [{:keys [gui-view world-view]}]
  {:gui-view (->gui-view gui-view)
   :world-view (->world-view world-view)})

(extend-type core.graphics.Graphics
  core.graphics/WorldView
  (world-unit-scale [{:keys [world-view]}]
    (:unit-scale world-view))

  (pixels->world-units [g pixels]
    (* (int pixels) (g/world-unit-scale g))))

(defn ^:no-doc gui-viewport   ^Viewport [g] (-> g :gui-view   :viewport))
(defn ^:no-doc world-viewport ^Viewport [g] (-> g :world-view :viewport))

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

(defn- gui-mouse-position* [g]
  ; TODO mapv int needed?
  (mapv int (unproject-mouse-posi (gui-viewport g))))

(defn- world-mouse-position* [g]
  ; TODO clamping only works for gui-viewport ? check. comment if true
  ; TODO ? "Can be negative coordinates, undefined cells."
  (unproject-mouse-posi (world-viewport g)))

(defn- gr [ctx] (:context/graphics ctx))

(defn gui-mouse-position    [ctx] (gui-mouse-position*   (gr ctx)))
(defn world-mouse-position  [ctx] (world-mouse-position* (gr ctx)))
(defn gui-viewport-width    [ctx] (.getWorldWidth  (gui-viewport   (gr ctx))))
(defn gui-viewport-height   [ctx] (.getWorldHeight (gui-viewport   (gr ctx))))
(defn world-camera          [ctx] (.getCamera      (world-viewport (gr ctx))))
(defn world-viewport-width  [ctx] (.getWorldWidth  (world-viewport (gr ctx))))
(defn world-viewport-height [ctx] (.getWorldHeight (world-viewport (gr ctx))))
