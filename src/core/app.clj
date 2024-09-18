(ns core.app
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [utils.core :refer [safe-merge]]
            [core.component :as component]
            [core.context :as ctx]
            [core.screen :as screen]
            [components.context.properties :as properties])
  (:import org.lwjgl.system.Configuration
           com.badlogic.gdx.ApplicationAdapter
           (com.badlogic.gdx.backends.lwjgl3 Lwjgl3Application Lwjgl3ApplicationConfiguration)
           com.badlogic.gdx.graphics.Color
           (com.badlogic.gdx.utils ScreenUtils SharedLibraryLoader)))

; screens require vis-ui / properties (map-editor, property editor uses properties)
(defn- context-create-order [[k _]]
  (if (= k :context/screens) 1 0))

(def state (atom nil))

(defn- ->application [context]
  (proxy [ApplicationAdapter] []
    (create []
      (->> context
           (sort-by context-create-order)
           (component/create-into (assoc context :context/state state))
           ctx/init-first-screen
           (reset! state)))

    (dispose []
      (run! component/destroy @state))

    (render []
      (ScreenUtils/clear Color/BLACK)
      (-> @state
          ctx/current-screen
          (screen/render! state)))

    (resize [w h]
      (ctx/update-viewports @state w h))))

(defn- ->lwjgl3-app-config [{:keys [title width height full-screen? fps]}]
  ; can remove :pre, we are having a schema now
  ; move schema here too ?
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
    ; See https://libgdx.com/wiki/graphics/querying-and-configuring-graphics
    ; but makes no difference
    #_com.badlogic.gdx.graphics.glutils.HdpiMode
    #_(.setHdpiMode config #_HdpiMode/Pixels HdpiMode/Logical)
    config))

(defn- require-all-components! []
  (let [component-namespaces (->> "src/components/"
                                  io/file
                                  file-seq
                                  (map java.io.File/.getPath)
                                  (filter #(str/ends-with? % ".clj"))
                                  (map #(-> %
                                            (str/replace "src/" "")
                                            (str/replace ".clj" "")
                                            (str/replace "/" ".")
                                            symbol)))]
    (run! require component-namespaces)))

; https://github.com/libgdx/libgdx/pull/7361
; Maybe can delete this when using that new libgdx version
; which includes this PR.
(when (SharedLibraryLoader/isMac)
  (.set Configuration/GLFW_LIBRARY_NAME "glfw_async")
  (.set Configuration/GLFW_CHECK_THREAD0 false))

(defn -main [& [file]]
  ; require in -main here and not top-level
  ; otherwise some problem with gdx.scene2d.actor/add-tooltip! (cyclic dependency)
  ; which requires state
  (require-all-components!)
  (let [ctx (assoc (ctx/->Context) :context/properties (properties/validate-and-create file))
        app (ctx/property ctx :app/core)]
    (Lwjgl3Application. (->application (safe-merge ctx (:app/context app)))
                        (->lwjgl3-app-config (:app/lwjgl3 app)))))
