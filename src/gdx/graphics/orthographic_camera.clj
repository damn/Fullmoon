(ns gdx.graphics.orthographic-camera
  (:import com.badlogic.gdx.graphics.OrthographicCamera))

(defn zoom [^OrthographicCamera camera]
  (.zoom camera))

(defn set-zoom! [^OrthographicCamera camera amount]
  (set! (.zoom camera) amount) ; float ?
  (.update camera)) ; TODO

(defn reset-zoom! [camera]
  (set-zoom! camera 1))
