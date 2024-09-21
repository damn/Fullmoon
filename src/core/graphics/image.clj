(ns core.graphics.image
  (:require [gdx.graphics.g2d :as g2d]
            [core.assets :as assets]
            [core.g :as g])
  (:import com.badlogic.gdx.graphics.Color
           (com.badlogic.gdx.graphics.g2d TextureRegion Batch)))

(defrecord Image [texture-region
                  pixel-dimensions
                  world-unit-dimensions
                  color]) ; optional

(defn- unit-dimensions [image unit-scale]
  (if (= unit-scale 1)
    (:pixel-dimensions image)
    (:world-unit-dimensions image)))

(defn- scale-dimensions [dimensions scale]
  (mapv (comp float (partial * scale)) dimensions))

(defn- texture-region-dimensions [^TextureRegion texture-region]
  [(.getRegionWidth  texture-region)
   (.getRegionHeight texture-region)])

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

(extend-type core.g.Graphics
  core.g/Image
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

(defn create [{g :context/graphics :as ctx} file]
  (->image g (g2d/->texture-region (assets/texture ctx file))))

(defn sub-image [{g :context/graphics} {:keys [texture-region]} bounds]
  (->image g (g2d/->texture-region texture-region bounds)))

(defn spritesheet [ctx file tilew tileh]
  {:image (create ctx file)
   :tilew tilew
   :tileh tileh})

(defn sprite
  "x,y index starting top-left"
  [ctx {:keys [image tilew tileh]} [x y]]
  (sub-image ctx
                 image
                 [(* x tilew) (* y tileh) tilew tileh]))

(defn edn->image [{:keys [file sub-image-bounds]} ctx]
  (if sub-image-bounds
    (let [[sprite-x sprite-y] (take 2 sub-image-bounds)
          [tilew tileh]       (drop 2 sub-image-bounds)]
      (sprite ctx
              (spritesheet ctx file tilew tileh)
              [(int (/ sprite-x tilew))
               (int (/ sprite-y tileh))]))
    (create ctx file)))

