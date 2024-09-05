(ns gdx.backends.lwjgl3
  (:import org.lwjgl.system.Configuration
           com.badlogic.gdx.utils.SharedLibraryLoader
           (com.badlogic.gdx.backends.lwjgl3 Lwjgl3Application
                                             Lwjgl3ApplicationConfiguration)
           #_com.badlogic.gdx.graphics.glutils.HdpiMode))

; https://github.com/libgdx/libgdx/pull/7361
; Maybe can delete this when using that new libgdx version
; which includes this PR.
(defn- fix-mac! []
  (when (SharedLibraryLoader/isMac)
    (.set Configuration/GLFW_LIBRARY_NAME "glfw_async")
    (.set Configuration/GLFW_CHECK_THREAD0 false)))

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
  (fix-mac!)
  (let [config (doto (Lwjgl3ApplicationConfiguration.)
                 (.setTitle title)
                 (.setForegroundFPS (or fps 60)))]
    (if full-screen?
      (.setFullscreenMode config (display-mode))
      (.setWindowedMode config width height))
    ; See https://libgdx.com/wiki/graphics/querying-and-configuring-graphics
    ; but makes no difference
    #_(.setHdpiMode config #_HdpiMode/Pixels HdpiMode/Logical)
    config))
