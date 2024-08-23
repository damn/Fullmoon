(ns graphics.image
  (:require [gdx.graphics.g2d :as g2d]
            [gdx.graphics.g2d.batch :as batch]
            [data.image :as image]
            [api.context :as ctx]))

(extend-type api.graphics.Graphics
  api.graphics/Image
  (draw-image [{:keys [batch unit-scale]}
               {:keys [texture-region color] :as image}
               position]
    (batch/draw-texture-region batch
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
      (batch/draw-texture-region batch
                                 texture-region
                                 [(- (float x) (/ (float w) 2))
                                  (- (float y) (/ (float h) 2))]
                                 [w h]
                                 rotation
                                 color)))

  (draw-centered-image [this image position]
    (g/draw-rotated-centered-image this image 0 position)))

(extend-type api.context.Context
  api.context/Images
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
