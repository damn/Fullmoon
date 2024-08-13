(ns gdx.utils.viewport
  (:import com.badlogic.gdx.utils.viewport.FitViewport))

(defn ->fit-viewport [world-width world-height camera]
  (FitViewport. world-width world-height camera))
