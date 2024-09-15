(ns gdx.graphics.g2d
  (:import com.badlogic.gdx.graphics.Texture
           com.badlogic.gdx.graphics.g2d.TextureRegion))

(defn ->texture-region
  ([^Texture texture]
   (TextureRegion. texture))

  ([^TextureRegion texture-region [x y w h]]
   (TextureRegion. texture-region (int x) (int y) (int w) (int h))))
