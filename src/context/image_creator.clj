(ns context.image-creator
  (:require [api.context :as ctx])
  (:import com.badlogic.gdx.graphics.g2d.TextureRegion))

(extend-type api.context.Context
  api.context/ImageCreator
  (create-image [{g :context/graphics :as ctx} file]
    (g/->image g (TextureRegion. (ctx/cached-texture ctx file))))

  (get-sub-image [{g :context/graphics} {:keys [texture-region]} [x y w h]]
    (g/->image g (TextureRegion. texture-region (int x) (int y) (int w) (int h))))

  (spritesheet [context file tilew tileh]
    {:image (ctx/create-image context file)
     :tilew tilew
     :tileh tileh})

  (get-sprite [context {:keys [image tilew tileh]} [x y]]
    (ctx/get-sub-image context
                       image
                       [(* x tilew) (* y tileh) tilew tileh]))

  ; TODO unused, untested.
  (get-scaled-copy [{{:keys [world-unit-scale]} :context/graphics} image scale]
    (-> image
        (assoc :scale scale)
        (assoc-dimensions world-unit-scale))))
