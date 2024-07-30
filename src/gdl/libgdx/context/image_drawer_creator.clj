(ns ^:no-doc gdl.libgdx.context.image-drawer-creator
  (:require [gdl.context :refer [cached-texture]])
  (:import com.badlogic.gdx.graphics.Texture
           com.badlogic.gdx.graphics.g2d.TextureRegion))

(defn- texture-dimensions [^TextureRegion texture]
  [(.getRegionWidth  texture)
   (.getRegionHeight texture)])

(defn- assoc-dimensions [{:keys [texture scale] :as image} world-unit-scale]
  {:pre [(number? world-unit-scale)
         (or (number? scale)
             (and (vector? scale)
                  (number? (scale 0))
                  (number? (scale 1))))]}
  (let [pixel-dimensions (if (number? scale)
                           (mapv (comp float (partial * scale)) (texture-dimensions texture))
                           scale)]
    (assoc image
           :pixel-dimensions pixel-dimensions
           :world-unit-dimensions (mapv (comp float (partial * world-unit-scale)) pixel-dimensions))))

; (.getTextureData (.getTexture (:texture (first (:frames (:animation @(game.db/get-entity 1)))))))
; can remove :file @ Image because its in texture-data
; only TextureRegion doesn't have toString , can implement myself ? so can see which image is being used (in case)
(defrecord Image [file ; -> is in texture data, can remove.
                  texture ; -region ?
                  sub-image-bounds ; => is in texture-region data?
                  scale
                  pixel-dimensions
                  world-unit-dimensions
                  tilew
                  tileh])
; color missing ?

(defn- ->texture-region [ctx file & [x y w h]]
  (let [^Texture texture (cached-texture ctx file)]
    (if (and x y w h)
      (TextureRegion. texture (int x) (int y) (int w) (int h))
      (TextureRegion. texture))))

; TODO pass texture-region ....

(extend-type gdl.context.Context
  gdl.context/ImageCreator
  (create-image [{{:keys [world-unit-scale]} :gdl.libgdx.context/graphics :as ctx} file]
    (assoc-dimensions (map->Image {:file file
                                   :scale 1 ; not used anymore as arg (or scale 1) because varargs protocol methods not possible, anyway refactor images
                                   ; take only texture-region, scale,color
                                   :texture (->texture-region ctx file)})
                      world-unit-scale))

  (get-scaled-copy [{{:keys [world-unit-scale]} :gdl.libgdx.context/graphics} image scale]
    (assoc-dimensions (assoc image :scale scale)
                      world-unit-scale))


  (get-sub-image [{{:keys [world-unit-scale]} :gdl.libgdx.context/graphics :as ctx}
                  {:keys [file sub-image-bounds] :as image}]
    (assoc-dimensions (assoc image
                             :scale 1
                             :texture (apply ->texture-region ctx file sub-image-bounds)
                             :sub-image-bounds sub-image-bounds)
                      world-unit-scale))

  (spritesheet [context file tilew tileh]
    (assoc (gdl.context/create-image context file) :tilew tilew :tileh tileh))

  (get-sprite [context {:keys [tilew tileh] :as sheet} [x y]]
    (gdl.context/get-sub-image context
                                 (assoc sheet :sub-image-bounds [(* x tilew) (* y tileh) tilew tileh]))))
