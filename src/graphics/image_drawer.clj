(ns graphics.image-drawer
  (:require [api.graphics :as g])
  (:import [com.badlogic.gdx.graphics Color Texture]
           [com.badlogic.gdx.graphics.g2d Batch TextureRegion]))

(defn- draw-texture-region [^Batch batch texture-region [x y] [w h] rotation color]
  (if color (.setColor batch color))
  (.draw batch texture-region
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

; TODO remove pixel-/wu-/scale- and just pass 'dimensions' to image ??
; the dimenions to draw the image at.....
; how does libgdx Sprite does it ??

; then all the view code will become easier too ... ?
; where unit-scale used???

; only @ text drawer ....


; create image
; default scale = texture-region itself pixel -dimensions ?

; => only where property image/animation goes to entity it draws to world units ...


; property/image
; property/animation
; entity/image

; where drawing draws in wu ?? check ...

; only @ entity/image \| entity\/animation & also draw-item-on-cursor / player-item-on-cursor draw world-item ....
; => only with property\/image => assoc world-unit-scale there?


; 1. of all for sure - move image-creator and serialization together with this code
; => need to pass texture-region ....
; => then we need to get file out again ....
; from texture-region ) ;
; and to string maybe ..

; 2. move spritesheet out, its a separate record ....

; 3. ctx fn ->image with file ??


(defn- unit-dimensions [image unit-scale]
  ;{:post [%]}
  ;(get (:unit-dimensions image) unit-scale)
  (if (= unit-scale 1)
    (:pixel-dimensions image)
    (:world-unit-dimensions image)))

(extend-type api.graphics.Graphics
  api.graphics/ImageDrawer
  (draw-image [{:keys [batch unit-scale]}
               {:keys [texture-region color] :as image}
               position]
    (draw-texture-region batch
                         texture-region
                         position
                         (unit-dimensions image unit-scale)
                         0 ; rotation
                         color))

  (draw-rotated-centered-image [{:keys [batch unit-scale]}
                                {:keys [texture-region color] :as image}
                                rotation
                                [x y]]
    (let [[w h] (unit-dimensions image unit-scale)]
      (draw-texture-region batch
                           texture-region
                           [(- (float x) (/ (float w) 2))
                            (- (float y) (/ (float h) 2))]
                           [w h]
                           rotation
                           color)))

  (draw-centered-image [this image position]
    (g/draw-rotated-centered-image this image 0 position)))

; vimgrep/draw-image\|draw-rotated-centered-image\|draw-centered-image/g src/** test/**
