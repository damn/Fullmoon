(ns clj.gdx.graphics.orthographic-camera
  (:import com.badlogic.gdx.graphics.OrthographicCamera))

(defn zoom [^OrthographicCamera camera]
  (.zoom camera))

(defn set-zoom! [^OrthographicCamera camera amount]
  (set! (.zoom camera) amount)
  (.update camera))

(defn reset-zoom! [^OrthographicCamera camera]
  (set! (.zoom camera) 1.0))
