(ns clj.gdx.graphics.g2d.texture-region
  (:import com.badlogic.gdx.graphics.g2d.TextureRegion))

; TODO naming ?
(defn dimensions [^TextureRegion texture-region]
  [(.getRegionWidth  texture-region)
   (.getRegionHeight texture-region)])
