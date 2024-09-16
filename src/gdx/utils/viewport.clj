(ns gdx.utils.viewport
  (:import com.badlogic.gdx.utils.viewport.Viewport))

(defn screen-width      [^Viewport viewport] (.getScreenWidth     viewport))
(defn screen-height     [^Viewport viewport] (.getScreenHeight    viewport))
(defn world-width       [^Viewport viewport] (.getWorldWidth      viewport))
(defn world-height      [^Viewport viewport] (.getWorldHeight     viewport))
(defn camera            [^Viewport viewport] (.getCamera          viewport))
(defn right-gutter-x    [^Viewport viewport] (.getRightGutterX    viewport))
(defn top-gutter-y      [^Viewport viewport] (.getTopGutterY      viewport))
(defn left-gutter-width [^Viewport viewport] (.getLeftGutterWidth viewport))
(defn top-gutter-height [^Viewport viewport] (.getTopGutterHeight viewport))
