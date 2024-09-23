(ns core.gdx.graphics
  (:import com.badlogic.gdx.graphics.Color))

(defn ->color
  ([r g b]
   (->color r g b 1))
  ([r g b a]
   (Color. (float r) (float g) (float b) (float a))))
