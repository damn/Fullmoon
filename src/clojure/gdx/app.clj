(ns clojure.gdx.app
  {:core.tool/icon "✅"}
  (:import (com.badlogic.gdx Gdx ApplicationAdapter)
           (com.badlogic.gdx.backends.lwjgl3 Lwjgl3Application Lwjgl3ApplicationConfiguration)
           (com.badlogic.gdx.utils SharedLibraryLoader)
           (org.lwjgl.system Configuration)))

(defprotocol Listener
  (create!  [_])
  (dispose! [_])
  (render!  [_])
  (resize!  [_ dimensions]))

; hi
(defn- macos-fix! []
  ; https://github.com/libgdx/libgdx/pull/7361
  ; Maybe can delete this when using that new libgdx version
  ; which includes this PR.
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
  (Lwjgl3Application. (proxy [ApplicationAdapter] []
                        (create  []    (create!  listener))
                        (dispose []    (dispose! listener))
                        (render  []    (render!  listener))
                        (resize  [w h] (resize!  listener [w h])))
                      (lwjgl3-config config)))

(defn exit! []
  (.exit Gdx/app))

(defmacro post-runnable! [& exprs]
  `(.postRunnable Gdx/app (fn [] ~@exprs)))
