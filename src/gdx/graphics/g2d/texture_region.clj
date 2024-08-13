(ns gdx.graphics.g2d.texture-region
  (:import com.badlogic.gdx.graphics.g2d.TextureRegion))

(defn dimensions [^TextureRegion texture-region]
  [(.getRegionWidth  texture-region)
   (.getRegionHeight texture-region)])
