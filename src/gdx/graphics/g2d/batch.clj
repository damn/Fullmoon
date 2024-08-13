(ns gdx.graphics.g2d.batch
  (:require [gdx.graphics.color :as color])
  (:import com.badlogic.gdx.graphics.g2d.Batch))

(defn begin [^Batch batch] (.begin batch))
(defn end   [^Batch batch] (.end   batch))

(defn set-color [^Batch batch color] (.setColor batch color))

(defn set-projection-matrix [^Batch batch matrix] (.setProjectionMatrix batch matrix))

; TODO [x y] is center or left-bottom ?
; why rotation origin calculations ?!
(defn draw-texture-region [^Batch batch texture-region [x y] [w h] rotation color]
  (if color (.setColor batch color)) ; TODO move out, simplify ....
  (.draw batch
         texture-region
         x
         y
         (/ (float w) 2) ; rotation origin
         (/ (float h) 2)
         w ; width height
         h
         1 ; scaling factor
         1
         rotation)
  (if color (.setColor batch color/white)))
