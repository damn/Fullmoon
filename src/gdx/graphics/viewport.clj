(ns gdx.graphics.viewport
  (:refer-clojure :exclude [update])
  (:import (com.badlogic.gdx Gdx)
           (com.badlogic.gdx.math MathUtils Vector2)
           (com.badlogic.gdx.utils.viewport Viewport)))

(defn- clamp [value min max]
  (MathUtils/clamp (float value) (float min) (float max)))

; touch coordinates are y-down, while screen coordinates are y-up
; so the clamping of y is reverse, but as black bars are equal it does not matter
(defn unproject-mouse-posi
  "Returns vector of [x y]."
  [^Viewport viewport]
  (let [mouse-x (clamp (.getX Gdx/input)
                       (.getLeftGutterWidth viewport)
                       (.getRightGutterX viewport))
        mouse-y (clamp (.getY Gdx/input)
                       (.getTopGutterHeight viewport)
                       (.getTopGutterY viewport))
        coords (.unproject viewport (Vector2. mouse-x mouse-y))]
    [(.x coords) (.y coords)]))

(defn world-width  [^Viewport vp] (.getWorldWidth  vp))
(defn world-height [^Viewport vp] (.getWorldHeight vp))
(defn camera       [^Viewport vp] (.getCamera      vp))
(defn update      [^Viewport vp [w h] & {:keys [center-camera?]}]
  (.update vp w h (boolean center-camera?)))
