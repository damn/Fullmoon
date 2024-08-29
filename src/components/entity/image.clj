(ns components.entity.image
  (:require [core.component :as component :refer [defcomponent]]
            [core.context :as ctx]
            [core.graphics :as g])
  (:import com.badlogic.gdx.graphics.g2d.TextureRegion))

; TODO to and from edn is actually not based on attribute but :data...

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

(defcomponent :entity/image
  {:data :image
   :optional? false
   :let image}
  (component/edn->value [[_ {:keys [file sub-image-bounds]}] ctx]
    (if sub-image-bounds
      (let [[sprite-x sprite-y] (take 2 sub-image-bounds)
            [tilew tileh]       (drop 2 sub-image-bounds)]
        (ctx/get-sprite ctx
                        (ctx/spritesheet ctx file tilew tileh)
                        [(int (/ sprite-x tilew))
                         (int (/ sprite-y tileh))]))
      (ctx/create-image ctx file)))

  ; not serializing color&scale
  (component/value->edn [[_ {:keys [texture-region]}]]
    (merge {:file (texture-region->file texture-region)}
           (if (is-sub-texture? texture-region)
             {:sub-image-bounds (region-bounds texture-region)})))

  (component/render-default [_ entity* g _ctx]
    (g/draw-rotated-centered-image g
                                   image
                                   (or (:rotation-angle entity*) 0)
                                   (:position entity*))))
