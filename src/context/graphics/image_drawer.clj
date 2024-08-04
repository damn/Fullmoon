(ns context.graphics.image-drawer
  (:require [api.graphics :as g])
  (:import com.badlogic.gdx.graphics.Color
           com.badlogic.gdx.graphics.g2d.Batch))

(defn- draw-texture [^Batch batch texture [x y] [w h] rotation color]
  (if color (.setColor batch color))
  (.draw batch texture ; TODO this is texture-region ?
         x
         y
         (/ (float w) 2) ; rotation origin
         (/ (float h) 2)
         w ; width height
         h
         1 ; scaling factor
         1
         rotation)
  (if color (.setColor batch Color/WHITE)))

; TODO just make in image map of unit-scales to dimensions for each view
; and get by view key ?
(defn- unit-dimensions [unit-scale image]
  (if (= unit-scale 1)
    (:pixel-dimensions image)
    (:world-unit-dimensions image)))

(extend-type api.graphics.Graphics
  api.graphics/ImageDrawer
  (draw-image [{:keys [batch unit-scale]}
               {:keys [texture color] :as image}
               position]
    (draw-texture batch texture position (unit-dimensions unit-scale image) 0 color))

  (draw-rotated-centered-image [{:keys [batch unit-scale]}
                                {:keys [texture color] :as image}
                                rotation
                                [x y]]
    (let [[w h] (unit-dimensions unit-scale image)]
      (draw-texture batch
                    texture
                    [(- (float x) (/ (float w) 2))
                     (- (float y) (/ (float h) 2))]
                    [w h]
                    rotation
                    color)))

  (draw-centered-image [this image position]
    (g/draw-rotated-centered-image this image 0 position)))
