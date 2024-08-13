(ns context.graphics.image
  (:require [clj.gdx.graphics.g2d :as g2d]
            [clj.gdx.graphics.g2d.batch :as batch]
            [api.context :as ctx]
            [api.graphics :as g]))

(defrecord Image [texture-region
                  pixel-dimensions
                  world-unit-dimensions
                  color]) ; optional

(defn- unit-dimensions [image unit-scale]
  (if (= unit-scale 1) ; TODO hardcoded gui-unit-scale .......
    (:pixel-dimensions image)
    (:world-unit-dimensions image)))

(defn- texture-region-dimensions [^TextureRegion texture-region]
  [(.getRegionWidth  texture-region)
   (.getRegionHeight texture-region)])

(defn- scale-dimensions [dimensions scale]
  (mapv (comp float (partial * scale)) dimensions))

(defn- assoc-dimensions
  "scale can be a number for multiplying the texture-region-dimensions or [w h]."
  [{:keys [texture-region] :as image} g scale]
  {:pre [(or (number? scale)
             (and (vector? scale)
                  (number? (scale 0))
                  (number? (scale 1))))]}
  (let [pixel-dimensions (if (number? scale)
                           (scale-dimensions (texture-region-dimensions texture-region) scale)
                           scale)]
    (assoc image
           :pixel-dimensions pixel-dimensions
           :world-unit-dimensions (scale-dimensions pixel-dimensions (g/world-unit-scale g)))))

(defn- ->image [g texture-region]
  (-> {:texture-region texture-region}
      (assoc-dimensions g 1)
      map->Image))

(extend-type api.graphics.Graphics
  api.graphics/Image
  (draw-image [{:keys [batch unit-scale]}
               {:keys [texture-region color] :as image}
               position]
    (batch/draw-texture-region batch
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
    (->image g (g2d/->texture-region (ctx/cached-texture ctx file))))

  (get-sub-image [{g :context/graphics} {:keys [texture-region]} bounds]
    (->image g (g2d/->texture-region texture-region bounds)))

  (spritesheet [ctx file tilew tileh]
    {:image (ctx/create-image ctx file)
     :tilew tilew
     :tileh tileh})

  (get-sprite [ctx {:keys [image tilew tileh]} [x y]]
    (ctx/get-sub-image ctx
                       image
                       [(* x tilew) (* y tileh) tilew tileh])))
