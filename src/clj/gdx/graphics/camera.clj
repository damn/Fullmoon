(ns clj.gdx.graphics.camera
  (:import com.badlogic.gdx.graphics.Camera))

(defn position [^Camera camera]
  [(.x (.position camera))
   (.y (.position camera))])

(defn set-position! [^Camera camera [x y]]
  (set! (.x (.position camera)) (float x))
  (set! (.y (.position camera)) (float y))
  (.update camera)) ; TODO no update ?

(defn frustum [^Camera camera] ; TODO namespaced keyword access data ?
  (.furstum camera))
