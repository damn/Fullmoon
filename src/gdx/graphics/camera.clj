(ns gdx.graphics.camera
  (:import com.badlogic.gdx.graphics.Camera))

(defn position
  "Returns camera position as [x y] vector."
  [^Camera camera]
  [(.x (.position camera))
   (.y (.position camera))])

(defn set-position!
  "Sets x and y and calls update on the camera."
  [^Camera camera [x y]]
  (set! (.x (.position camera)) (float x))
  (set! (.y (.position camera)) (float y))
  (.update camera))
