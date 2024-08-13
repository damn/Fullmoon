(ns gdx.backends.lwjgl3
  (:import (com.badlogic.gdx.backends.lwjgl3 Lwjgl3Application Lwjgl3ApplicationConfiguration)))

(defn ->application [application-listener lwjgl3-configuration]
  (Lwjgl3Application. application-listener lwjgl3-configuration))

; TODO fullscreen bug .
(defn ->configuration [{:keys [title width height full-screen? fps]}]
  {:pre [title
         width
         height
         (boolean? full-screen?)
         (or (nil? fps) (int? fps))]}
  (let [config (doto (Lwjgl3ApplicationConfiguration.)
                 (.setTitle title)
                 (.setForegroundFPS (or fps 60)))]
    (if full-screen?
      (.setFullscreenMode config (Lwjgl3ApplicationConfiguration/getDisplayMode))
      (.setWindowedMode config width height))
    config))
