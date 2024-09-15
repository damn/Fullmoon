(ns components.graphics.image
  (:require [gdx.graphics.g2d :as g2d]
            [core.image :as image]
            [core.context :as ctx]
            [core.graphics :as g])
  (:import com.badlogic.gdx.graphics.Color
           com.badlogic.gdx.graphics.g2d.Batch))

; TODO [x y] is center or left-bottom ?
; why rotation origin calculations ?!
(defn- draw-texture-region [^Batch batch texture-region [x y] [w h] rotation color]
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
  (if color (.setColor batch Color/WHITE)))

(extend-type core.graphics.Graphics
  core.graphics/Image
  (draw-image [{:keys [batch unit-scale]}
               {:keys [texture-region color] :as image}
               position]
    (draw-texture-region batch
                         texture-region
                         position
                         (image/unit-dimensions image unit-scale)
                         0 ; rotation
                         color))

  (draw-rotated-centered-image [{:keys [batch unit-scale]}
                                {:keys [texture-region color] :as image}
                                rotation
                                [x y]]
    (let [[w h] (image/unit-dimensions image unit-scale)]
      (draw-texture-region batch
                           texture-region
                           [(- (float x) (/ (float w) 2))
                            (- (float y) (/ (float h) 2))]
                           [w h]
                           rotation
                           color)))

  (draw-centered-image [this image position]
    (g/draw-rotated-centered-image this image 0 position)))

(extend-type core.context.Context
  core.context/Images
  (create-image [{g :context/graphics :as ctx} file]
    (image/->image g (g2d/->texture-region (ctx/cached-texture ctx file))))

  (get-sub-image [{g :context/graphics} {:keys [texture-region]} bounds]
    (image/->image g (g2d/->texture-region texture-region bounds)))

  (spritesheet [ctx file tilew tileh]
    {:image (ctx/create-image ctx file)
     :tilew tilew
     :tileh tileh})

  (get-sprite [ctx {:keys [image tilew tileh]} [x y]]
    (ctx/get-sub-image ctx
                       image
                       [(* x tilew) (* y tileh) tilew tileh])))
