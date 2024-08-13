(ns clj.gdx.graphics.g2d
  (:import (com.badlogic.gdx.graphics.g2d TextureRegion
                                          SpriteBatch)))

(defn ->texture-region
  ([texture]
   (TextureRegion. texture))

  ([texture-region [x y w h]]
   (TextureRegion. texture (int x) (int y) (int w) (int h))))

(defn ->sprite-batch []
  (SpriteBatch.))
