(ns core.image
  (:require [gdx.graphics.g2d.texture-region :as texture-region]
            [core.context :as ctx]
            [core.graphics :as g])
  (:import com.badlogic.gdx.graphics.g2d.TextureRegion))

(defrecord Image [texture-region
                  pixel-dimensions
                  world-unit-dimensions
                  color]) ; optional

(defn unit-dimensions [image unit-scale]
  (if (= unit-scale 1)
    (:pixel-dimensions image)
    (:world-unit-dimensions image)))

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
                           (scale-dimensions (texture-region/dimensions texture-region) scale)
                           scale)]
    (assoc image
           :pixel-dimensions pixel-dimensions
           :world-unit-dimensions (scale-dimensions pixel-dimensions (g/world-unit-scale g)))))

(defn ->image [g texture-region]
  (-> {:texture-region texture-region}
      (assoc-dimensions g 1)
      map->Image))

(defn edn->image [{:keys [file sub-image-bounds]} ctx]
  (if sub-image-bounds
    (let [[sprite-x sprite-y] (take 2 sub-image-bounds)
          [tilew tileh]       (drop 2 sub-image-bounds)]
      (ctx/get-sprite ctx
                      (ctx/spritesheet ctx file tilew tileh)
                      [(int (/ sprite-x tilew))
                       (int (/ sprite-y tileh))]))
    (ctx/create-image ctx file)))

(defn- is-sub-texture? [^TextureRegion texture-region]
  (let [texture (.getTexture texture-region)]
    (or (not= (.getRegionWidth  texture-region) (.getWidth  texture))
        (not= (.getRegionHeight texture-region) (.getHeight texture)))))

(defn- region-bounds [^TextureRegion texture-region]
  [(.getRegionX texture-region)
   (.getRegionY texture-region)
   (.getRegionWidth texture-region)
   (.getRegionHeight texture-region)])

(defn- texture-region->file [^TextureRegion texture-region]
  (.toString (.getTextureData (.getTexture texture-region))))

; not serializing color&scale
(defn image->edn [{:keys [texture-region]}]
  (merge {:file (texture-region->file texture-region)}
         (if (is-sub-texture? texture-region)
           {:sub-image-bounds (region-bounds texture-region)})))
