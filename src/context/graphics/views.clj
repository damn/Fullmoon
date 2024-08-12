(ns context.graphics.views
  (:require api.context
            [api.graphics :as g])
  (:import com.badlogic.gdx.Gdx
           [com.badlogic.gdx.graphics Color OrthographicCamera]
           com.badlogic.gdx.graphics.g2d.Batch
           [com.badlogic.gdx.utils.viewport Viewport FitViewport]
           [com.badlogic.gdx.math MathUtils Vector2]))

(defn- screen-width  [] (.getWidth  Gdx/graphics))
(defn- screen-height [] (.getHeight Gdx/graphics))

(def ^:private gui-unit-scale 1)

(defn- ->gui-view []
  {:unit-scale gui-unit-scale
   :viewport (FitViewport. (graphics/width)
                           (graphics/height)
                           (OrthographicCamera.))})

(defn- ->world-view [{:keys [tile-size]}]
  (let [unit-scale (/ tile-size)]
    {:unit-scale (float unit-scale)
     :viewport (let [width  (* (screen-width)  unit-scale)
                     height (* (screen-height) unit-scale)
                     camera (OrthographicCamera.)
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
  (or (not= (.getScreenWidth  (gui-viewport g)) (screen-width))
      (not= (.getScreenHeight (gui-viewport g)) (screen-height))))

; on mac osx, when resizing window, make bug report /  fix it in libgdx
(defn fix-viewport-update
  "Sometimes the viewport update is not triggered."
  [context]
  (when (viewport-fix-required? context)
    (update-viewports context (screen-width) (screen-height))))

(defn- render-view [{{:keys [^Batch batch shape-drawer] :as g} :context/graphics}
                    view-key
                    draw-fn]
  (let [{:keys [^Viewport viewport unit-scale]} (view-key g)]
    (.setColor batch Color/WHITE) ; fix scene2d.ui.tooltip flickering
    (.setProjectionMatrix batch (.combined (.getCamera viewport)))
    (.begin batch)
    (g/with-shape-line-width g
                             unit-scale
                             #(draw-fn (assoc g :unit-scale unit-scale)))
    (.end batch)))

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
