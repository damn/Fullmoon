(ns gdx.utils.viewport.viewport
  (:refer-clojure :exclude [update])
  (:import com.badlogic.gdx.math.Vector2
           com.badlogic.gdx.utils.viewport.Viewport))

(defn screen-width      [^Viewport viewport] (.getScreenWidth     viewport))
(defn screen-height     [^Viewport viewport] (.getScreenHeight    viewport))
(defn world-width       [^Viewport viewport] (.getWorldWidth      viewport))
(defn world-height      [^Viewport viewport] (.getWorldHeight     viewport))
(defn camera            [^Viewport viewport] (.getCamera          viewport))
(defn right-gutter-x    [^Viewport viewport] (.getRightGutterX    viewport))
(defn top-gutter-y      [^Viewport viewport] (.getTopGutterY      viewport))
(defn left-gutter-width [^Viewport viewport] (.getLeftGutterWidth viewport))
(defn top-gutter-height [^Viewport viewport] (.getTopGutterHeight viewport))

(defn update [^Viewport viewport screen-width screen-height center-camera?]
  (.update viewport screen-width screen-height center-camera?))

; TODO Take & return clojure [x y] vector
(defn unproject ^Vector2 [^Viewport viewport ^Vector2 vector2]
  (.unproject viewport vector2))
