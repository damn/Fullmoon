(ns clj.gdx.graphics.camera
  (:import com.badlogic.gdx.graphics.Camera))

(defn set-position! [^Camera camera [x y]]
  (set! (.x (.position camera)) (float x))
  (set! (.y (.position camera)) (float y))
  (.update camera)) ; TODO no update ?

(comment
; TODO namespaced keyword access data ?
 (let [{:camera/keys [position
                      viewport-width
                      viewport-height]} camera]

   )
)

(defn frustum [^Camera camera]
  (.furstum camera))

(defn position [^Camera camera]
  [(.x (.position camera))
   (.y (.position camera))])

(defn viewport-width  [camera] (.viewportWidth  camera))
(defn viewport-height [camera] (.viewportHeight camera))
