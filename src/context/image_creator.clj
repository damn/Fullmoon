(ns context.image-creator
  (:require [api.context :as ctx])
  (:import com.badlogic.gdx.graphics.Texture
           com.badlogic.gdx.graphics.g2d.TextureRegion))

(defn- texture-region-dimensions [^TextureRegion texture-region]
  [(.getRegionWidth  texture-region)
   (.getRegionHeight texture-region)])

(defn- scale-dimensions [dimensions scale]
  (mapv (comp float (partial * scale)) dimensions))

(defn- assoc-dimensions [{:keys [texture-region scale] :as image} world-unit-scale]
  {:pre [(number? world-unit-scale)
         (or (number? scale)
             (and (vector? scale)
                  (number? (scale 0))
                  (number? (scale 1))))]}
  (let [pixel-dimensions (if (number? scale)
                           (scale-dimensions (texture-region-dimensions texture-region) scale)
                           scale)]
    (assoc image
           :pixel-dimensions pixel-dimensions
           :world-unit-dimensions (scale-dimensions pixel-dimensions world-unit-scale))))

(defrecord Image [texture-region
                  pixel-dimensions
                  world-unit-dimensions
                  color ; optional
                  ;;
                  scale ; number for mult. or [w h] -> creates px/wu dim. === is TODO _UNUSED_ ???
                  ])

(extend-type api.context.Context
  api.context/ImageCreator
  (create-image [{{:keys [world-unit-scale]} :context/graphics :as ctx} file]
    (-> {:texture-region (TextureRegion. (ctx/cached-texture ctx file))
         :scale 1}
        (assoc-dimensions world-unit-scale)
        map->Image))

  (get-sub-image [{{:keys [world-unit-scale]} :context/graphics :as ctx}
                  {:keys [texture-region]}
                  [x y w h]]
    (-> {:texture-region (TextureRegion. texture-region (int x) (int y) (int w) (int h))
         :scale 1}
        (assoc-dimensions world-unit-scale)
        map->Image))

  ; TODO unused, untested.
  (get-scaled-copy [{{:keys [world-unit-scale]} :context/graphics} image scale]
    (-> image
        (assoc :scale scale)
        (assoc-dimensions world-unit-scale)))

  (spritesheet [context file tilew tileh]
    {:image (ctx/create-image context file)
     :tilew tilew
     :tileh tileh})

  (get-sprite [context {:keys [image tilew tileh]} [x y]]
    (ctx/get-sub-image context
                       image
                       [(* x tilew) (* y tileh) tilew tileh])))
