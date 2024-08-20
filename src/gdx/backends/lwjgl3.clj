(ns gdx.backends.lwjgl3
  (:import (com.badlogic.gdx.backends.lwjgl3 Lwjgl3Application
                                             Lwjgl3ApplicationConfiguration)))

(defn ->application [application-listener lwjgl3-configuration]
  (Lwjgl3Application. application-listener lwjgl3-configuration))

(defn- display-mode []
  (Lwjgl3ApplicationConfiguration/getDisplayMode))

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
      (.setFullscreenMode config (display-mode))
      (.setWindowedMode config width height))
    ;(.setHdpiMode config)
    config))

(comment
 (clojure.pprint/pprint (seq (Lwjgl3ApplicationConfiguration/getDisplayModes)))
 (Lwjgl3ApplicationConfiguration/getDisplayMode)
 )
