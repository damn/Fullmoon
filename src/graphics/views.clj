(ns graphics.views
  (:require api.graphics)
  (:import com.badlogic.gdx.Gdx
           com.badlogic.gdx.graphics.OrthographicCamera
           [com.badlogic.gdx.utils.viewport Viewport FitViewport]
           [com.badlogic.gdx.math MathUtils Vector2]))

(defn- screen-width  [] (.getWidth  Gdx/graphics))
(defn- screen-height [] (.getHeight Gdx/graphics))

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

(def ^:private gui-unit-scale 1)

(defn update-viewports [{{:keys [gui-viewport world-viewport]} :context/graphics} w h]
  (.update ^Viewport gui-viewport w h true)
  ; Do not center the camera on world-viewport. We set the position there manually.
  (.update ^Viewport world-viewport w h false))

(defn- viewport-fix-required? [{{:keys [^Viewport gui-viewport]} :context/graphics}]
  (or (not= (.getScreenWidth  gui-viewport) (screen-width))
      (not= (.getScreenHeight gui-viewport) (screen-height))))

; on mac osx, when resizing window, make bug report /  fix it in libgdx
(defn fix-viewport-update
  "Sometimes the viewport update is not triggered."
  [context]
  (when (viewport-fix-required? context)
    (update-viewports context (screen-width) (screen-height))))

(extend-type api.graphics.Graphics
  api.graphics/GuiWorldViews
  (gui-mouse-position [{:keys [gui-viewport]}]
    ; TODO mapv int needed?
    (mapv int (unproject-mouse-posi gui-viewport)))

  (world-mouse-position [{:keys [world-viewport]}]
    ; TODO clamping only works for gui-viewport ? check. comment if true
    ; TODO ? "Can be negative coordinates, undefined cells."
    (unproject-mouse-posi world-viewport))

  (pixels->world-units [{:keys [world-unit-scale]} pixels]
    (* (int pixels) (float world-unit-scale))))

(defn- ->gui-view []
  {:gui-viewport (FitViewport. (screen-width)
                               (screen-height)
                               (OrthographicCamera.))})

(defn- ->world-view [{:keys [tile-size]}]
  (let [unit-scale (/ tile-size)]
    {:world-unit-scale (float unit-scale)
     :world-viewport (let [width  (* (screen-width)  unit-scale)
                           height (* (screen-height) unit-scale)
                           camera (OrthographicCamera.)
                           y-down? false]
                       (.setToOrtho camera y-down? width height)
                       (FitViewport. width height camera))}))

(defn ->build [world-view]
  (merge {:unit-scale gui-unit-scale} ; only here because actors want to use drawing without using render-gui-view -> @ context.ui I could pass the gui-unit-scale .....
         (->gui-view)
         (when world-view (->world-view world-view))))
