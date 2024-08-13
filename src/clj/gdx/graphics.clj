(ns clj.gdx.graphics
  (:import com.badlogic.gdx.Gdx
           (com.badlogic.gdx.graphics Color
                                      OrthographicCamera
                                      Pixmap)))

(defn delta-time
  "The time span between the current frame and the last frame in seconds."
  []
  (.getDeltaTime Gdx/graphics))

(defn frames-per-second
  "The average number of frames per second"
  []
  (.getFramesPerSecond Gdx/graphics))

(defn width []
  (.getWidth Gdx/graphics))

(defn height []
  (.getHeight Gdx/graphics))

(defn ->cursor [pixmap [hotspot-x hotspot-y]]
  (.newCursor Gdx/graphics pixmap hotspot-x hotspot-y))

(defn set-cursor [cursor]
  (.setCursor Gdx/graphics cursor))

(defn ->pixmap [file]
  (Pixmap. file))

(defn ->color
  ([r g b]
   (->color r g b 1))
  ([r g b a]
   (Color. (float r) (float g) (float b) (float a))))

(defn ->orthographic-camera []
  (OrthographicCamera.))
