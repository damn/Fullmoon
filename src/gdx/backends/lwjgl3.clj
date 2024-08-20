(ns gdx.backends.lwjgl3
  (:import (com.badlogic.gdx.backends.lwjgl3 Lwjgl3Application
                                             Lwjgl3ApplicationConfiguration)
           #_com.badlogic.gdx.graphics.glutils.HdpiMode))

(defn ->application [application-listener lwjgl3-configuration]
  (Lwjgl3Application. application-listener lwjgl3-configuration))

(defn- display-mode []
  (Lwjgl3ApplicationConfiguration/getDisplayMode))

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
      (.setFullscreenMode config (display-mode))
      (.setWindowedMode config width height))
    #_(.setHdpiMode config #_HdpiMode/Pixels HdpiMode/Logical)
    config))
