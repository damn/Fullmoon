(ns context.graphics.views
  (:require [gdx.graphics :as graphics]
            [gdx.graphics.color :as color]
            [gdx.graphics.camera :as camera]
            [gdx.graphics.g2d.batch :as batch]
            [gdx.input :as input]
            [gdx.utils.viewport :refer [->fit-viewport]]
            [gdx.utils.viewport.viewport :as viewport]
            api.context
            [api.graphics :as g])
  (:import [com.badlogic.gdx.math MathUtils Vector2]))

(defn- ->gui-view []
  {:unit-scale 1
   :viewport (->fit-viewport (graphics/width)
                             (graphics/height)
                             (graphics/->orthographic-camera))})

(defn- ->world-view [{:keys [tile-size]}]
  (let [unit-scale (/ tile-size)]
    {:unit-scale (float unit-scale)
     :viewport (let [world-width  (* (graphics/width)  unit-scale)
                     world-height (* (graphics/height) unit-scale)
                     camera (graphics/->orthographic-camera)
                     y-down? false]
                 (.setToOrtho camera y-down? world-width world-height)
                 (->fit-viewport world-width
                                 world-height
                                 camera))}))

(defn ->build [world-view]
  {:gui-view (->gui-view)
   :world-view (->world-view world-view)})

(extend-type api.graphics.Graphics
  api.graphics/WorldView
  (world-unit-scale [{:keys [world-view]}]
    (:unit-scale world-view))

  (pixels->world-units [g pixels]
    (* (int pixels) (g/world-unit-scale g))))

(defn- gui-viewport   [g] (-> g :gui-view   :viewport))
(defn- world-viewport [g] (-> g :world-view :viewport))

(defn update-viewports [{g :context/graphics} w h]
  (viewport/update (gui-viewport g) w h true)
  ; Do not center the camera on world-viewport. We set the position there manually.
  (viewport/update (world-viewport g) w h false))

(defn- viewport-fix-required? [{g :context/graphics}]
  (or (not= (viewport/screen-width  (gui-viewport g)) (graphics/width))
      (not= (viewport/screen-height (gui-viewport g)) (graphics/height))))

; on mac osx, when resizing window, make bug report /  fix it in libgdx
(defn fix-viewport-update
  "Sometimes the viewport update is not triggered."
  [context]
  (when (viewport-fix-required? context)
    (update-viewports context (graphics/width) (graphics/height))))

(defn- render-view [{{:keys [batch shape-drawer] :as g} :context/graphics}
                    view-key
                    draw-fn]
  (let [{:keys [viewport unit-scale]} (view-key g)]
    (batch/set-color batch color/white) ; fix scene2d.ui.tooltip flickering
    (batch/set-projection-matrix batch (camera/combined (viewport/camera viewport)))
    (batch/begin batch)
    (g/with-shape-line-width g
                             unit-scale
                             #(draw-fn (assoc g :unit-scale unit-scale)))
    (batch/end batch)))

(defn- clamp [value min max]
  (MathUtils/clamp (float value) (float min) (float max)))

; touch coordinates are y-down, while screen coordinates are y-up
; so the clamping of y is reverse, but as black bars are equal it does not matter
(defn- unproject-mouse-posi [viewport]
  (let [mouse-x (clamp (input/x)
                       (viewport/left-gutter-width viewport)
                       (viewport/right-gutter-x viewport))
        mouse-y (clamp (input/y)
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

(extend-type api.context.Context
  api.context/Views
  (render-gui-view   [ctx render-fn] (render-view ctx :gui-view   render-fn))
  (render-world-view [ctx render-fn] (render-view ctx :world-view render-fn))
  (gui-mouse-position    [ctx] (gui-mouse-position              (gr ctx)))
  (world-mouse-position  [ctx] (world-mouse-position            (gr ctx)))
  (gui-viewport-width    [ctx] (viewport/world-width  (gui-viewport   (gr ctx))))
  (gui-viewport-height   [ctx] (viewport/world-height (gui-viewport   (gr ctx))))
  (world-camera          [ctx] (viewport/camera       (world-viewport (gr ctx))))
  (world-viewport-width  [ctx] (viewport/world-width  (world-viewport (gr ctx))))
  (world-viewport-height [ctx] (viewport/world-height (world-viewport (gr ctx)))))
