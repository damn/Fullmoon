(ns context.graphics.views
  (:require [clj.gdx.graphics :as graphics]
            [clj.gdx.graphics.color :as color]
            [clj.gdx.graphics.g2d.batch :as batch]
            [clj.gdx.input :as input]
            api.context
            [api.graphics :as g])
  (:import [com.badlogic.gdx.utils.viewport Viewport FitViewport]
           [com.badlogic.gdx.math MathUtils Vector2]))

(def ^:private gui-unit-scale 1)

(defn- ->gui-view []
  {:unit-scale gui-unit-scale
   :viewport (FitViewport. (graphics/width)
                           (graphics/height)
                           (->orthographic-camera))})

(defn- ->world-view [{:keys [tile-size]}]
  (let [unit-scale (/ tile-size)]
    {:unit-scale (float unit-scale)
     :viewport (let [width  (* (graphics/width)  unit-scale)
                     height (* (graphics/height) unit-scale)
                     camera (->orthographic-camera)
                     y-down? false]
                 (.setToOrtho camera y-down? width height)
                 (FitViewport. width height camera))}))

(defn ->build [world-view]
  {:unit-scale gui-unit-scale ; only here because actors want to use drawing without using render-gui-view -> @ context.ui I could pass the gui-unit-scale .....
   :gui-view (->gui-view)
   :world-view (->world-view world-view)})

(extend-type api.graphics.Graphics
  api.graphics/WorldView
  (world-unit-scale [{:keys [world-view]}]
    (:unit-scale world-view))

  (pixels->world-units [g pixels]
    (* (int pixels) (g/world-unit-scale g))))

(defn- gui-viewport   ^Viewport [g] (-> g :gui-view   :viewport))
(defn- world-viewport ^Viewport [g] (-> g :world-view :viewport))

(defn update-viewports [{g :context/graphics} w h]
  (.update ^Viewport (gui-viewport g) w h true)
  ; Do not center the camera on world-viewport. We set the position there manually.
  (.update ^Viewport (world-viewport g) w h false))

(defn- viewport-fix-required? [{g :context/graphics}]
  (or (not= (.getScreenWidth  (gui-viewport g)) (graphics/width))
      (not= (.getScreenHeight (gui-viewport g)) (graphics/height))))

; on mac osx, when resizing window, make bug report /  fix it in libgdx
(defn fix-viewport-update
  "Sometimes the viewport update is not triggered."
  [context]
  (when (viewport-fix-required? context)
    (update-viewports context (screen-width) (screen-height))))

(defn- render-view [{{:keys [batch shape-drawer] :as g} :context/graphics}
                    view-key
                    draw-fn]
  (let [{:keys [^Viewport viewport unit-scale]} (view-key g)]
    (.setColor batch color/white) ; fix scene2d.ui.tooltip flickering
    (.setProjectionMatrix batch (.combined (.getCamera viewport)))
    (batch/begin batch)
    (g/with-shape-line-width g
                             unit-scale
                             #(draw-fn (assoc g :unit-scale unit-scale)))
    (batch/begin batch)))

(defn- clamp [value min max]
  (MathUtils/clamp (float value) (float min) (float max)))

; touch coordinates are y-down, while screen coordinates are y-up
; so the clamping of y is reverse, but as black bars are equal it does not matter
(defn- unproject-mouse-posi [^Viewport viewport]
  (let [mouse-x (clamp (input/x)
                       (.getLeftGutterWidth viewport)
                       (.getRightGutterX viewport))
        mouse-y (clamp (input/y)
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

(extend-type api.context.Context
  api.context/Views
  (render-gui-view   [ctx render-fn] (render-view ctx :gui-view   render-fn))
  (render-world-view [ctx render-fn] (render-view ctx :world-view render-fn))
  (gui-mouse-position    [ctx] (gui-mouse-position              (gr ctx)))
  (world-mouse-position  [ctx] (world-mouse-position            (gr ctx)))
  (gui-viewport-width    [ctx] (.getWorldWidth  (gui-viewport   (gr ctx))))
  (gui-viewport-height   [ctx] (.getWorldHeight (gui-viewport   (gr ctx))))
  (world-camera          [ctx] (.getCamera      (world-viewport (gr ctx))))
  (world-viewport-width  [ctx] (.getWorldWidth  (world-viewport (gr ctx))))
  (world-viewport-height [ctx] (.getWorldHeight (world-viewport (gr ctx)))))
