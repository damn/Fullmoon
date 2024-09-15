(ns app
  (:require [clojure.string :as str]
            clojure.java.io
            [utils.core :refer [safe-merge]]
            [gdx.graphics.color :as color]
            [core.component :as component]
            [core.context :as ctx]
            [components.context.properties :as properties])
  (:import org.lwjgl.system.Configuration
           com.badlogic.gdx.ApplicationAdapter
           (com.badlogic.gdx.utils ScreenUtils SharedLibraryLoader)
           (com.badlogic.gdx.backends.lwjgl3 Lwjgl3Application Lwjgl3ApplicationConfiguration)
           #_com.badlogic.gdx.graphics.glutils.HdpiMode))

(def state (atom nil))

; screens require vis-ui / properties (map-editor, property editor uses properties)
(defn- context-create-order [[k _]]
  (if (= k :context/screens) 1 0))

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
      (ScreenUtils/clear color/black)
      (-> @state
          ctx/current-screen
          (component/render! state)))

    (resize [w h]
      (ctx/update-viewports @state w h))))

(defn- component-namespaces []
  (filter #(str/ends-with? % ".clj")
          (map (memfn getPath)
               (file-seq (clojure.java.io/file "src/components/")))))

(defn- require-all-components! []
  (doseq [file (component-namespaces)
          :let [ns (-> file
                       (str/replace "src/" "")
                       (str/replace ".clj" "")
                       (str/replace "/" ".")
                       symbol)]]
    (when-not (find-ns ns)
      (require ns))))

; https://github.com/libgdx/libgdx/pull/7361
; Maybe can delete this when using that new libgdx version
; which includes this PR.
(defn- fix-mac! []
  (when (SharedLibraryLoader/isMac)
    (.set Configuration/GLFW_LIBRARY_NAME "glfw_async")
    (.set Configuration/GLFW_CHECK_THREAD0 false)))

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
    ; See https://libgdx.com/wiki/graphics/querying-and-configuring-graphics
    ; but makes no difference
    #_(.setHdpiMode config #_HdpiMode/Pixels HdpiMode/Logical)
    config))

(defn -main [& [file]]
  (require-all-components!)
  (fix-mac!)
  (let [ctx (assoc (ctx/->Context) :context/properties (properties/validate-and-create file))
        app (ctx/property ctx :app/core)]
    (Lwjgl3Application. (->application (safe-merge ctx (:app/context app)))
                        (lwjgl3/->configuration (:app/lwjgl3 app)))))
