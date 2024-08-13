(ns gdx.graphics.camera
  (:refer-clojure :exclude [update])
  (:import com.badlogic.gdx.graphics.Camera))

; TODO include always ?
#_(defn update [^Camera camera]
  (.update camera))

(defn set-position! [^Camera camera [x y]]
  (set! (.x (.position camera)) (float x))
  (set! (.y (.position camera)) (float y))
  (.update camera))

(defn frustum [^Camera camera]
  (.frustum camera))

(defn position [^Camera camera]
  [(.x (.position camera))
   (.y (.position camera))])

(defn viewport-width  [^Camera camera] (.viewportWidth  camera))
(defn viewport-height [^Camera camera] (.viewportHeight camera))

(defn combined [^Camera camera]
  (.combined camera))
