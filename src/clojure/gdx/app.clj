(ns clojure.gdx.app
  (:import (com.badlogic.gdx Gdx)
           (com.badlogic.gdx.backends.lwjgl3 Lwjgl3Application Lwjgl3ApplicationConfiguration)
           (com.badlogic.gdx.utils SharedLibraryLoader)
           (org.lwjgl.system Configuration)))

(defn- macos-fix! []
  (when SharedLibraryLoader/isMac
    (.set Configuration/GLFW_LIBRARY_NAME "glfw_async")
    (.set Configuration/GLFW_CHECK_THREAD0 false)))

(defn- lwjgl3-config
  [{:keys [title width height full-screen? fps]}]
  {:pre [title width height (boolean? full-screen?) (or (nil? fps) (int? fps))]}
  (let [config (doto (Lwjgl3ApplicationConfiguration.)
                 (.setTitle title)
                 (.setForegroundFPS (or fps 60)))]
    (if full-screen?
      (.setFullscreenMode config (Lwjgl3ApplicationConfiguration/getDisplayMode))
      (.setWindowedMode config width height))
    config))

(defn start! [listener config]
  (macos-fix!)
  (Lwjgl3Application. listener (lwjgl3-config config)))

(defn exit! []
  (.exit Gdx/app))

(defmacro post-runnable! [& exprs]
  `(.postRunnable Gdx/app (fn [] ~@exprs)))
