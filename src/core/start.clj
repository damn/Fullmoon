(ns core.start
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [core.ctx :refer :all]
            [core.graphics :as graphics]
            [core.property :as property])
  (:import org.lwjgl.system.Configuration
           com.badlogic.gdx.Gdx
           com.badlogic.gdx.ApplicationAdapter
           (com.badlogic.gdx.backends.lwjgl3 Lwjgl3Application Lwjgl3ApplicationConfiguration)
           com.badlogic.gdx.graphics.Color
           (com.badlogic.gdx.utils ScreenUtils SharedLibraryLoader)))

(defn- set-first-screen [context]
  (->> context
       :context/screens
       :first-screen
       (change-screen context)))

(defn- render! [app-state]
  (screen-render! (current-screen @app-state) app-state))

(defn- ->application [context]
  (proxy [ApplicationAdapter] []
    (create []
      (.bindRoot #'gdx-app      Gdx/app)
      (.bindRoot #'gdx-files    Gdx/files)
      (.bindRoot #'gdx-input    Gdx/input)
      (.bindRoot #'gdx-graphics Gdx/graphics)
      (->> context
           ; screens require vis-ui / properties (map-editor, property editor uses properties)
           (sort-by (fn [[k _]] (if (= k :context/screens) 1 0)))
           (create-into context)
           screens/set-first-screen
           (reset! app-state)))

    (dispose []
      (run! destroy! @app-state))

    (render []
      (ScreenUtils/clear Color/BLACK)
      (screens/render! app-state))

    (resize [w h]
      ; TODO fix mac screen resize bug again
      (graphics/on-resize @app-state w h))))

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
  (let [component-namespaces (->> "src/core/"
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

(def ^:private properties-edn-file "resources/properties.edn")

(defn -main []
  (require-all-components!)
  (let [ctx (map->Context {:context/properties (property/validate-and-create properties-edn-file)})
        app (build-property ctx :app/core)]
    (Lwjgl3Application. (->application (safe-merge ctx (:app/context app)))
                        (->lwjgl3-app-config (:app/lwjgl3 app)))))

(def-attributes
  :fps          :nat-int
  :full-screen? :boolean
  :width        :nat-int
  :height       :nat-int
  :title        :string)

(defcomponent :app/lwjgl3 {:data [:map [:fps
                                        :full-screen?
                                        :width
                                        :height
                                        :title]]})

(defcomponent :app/context
  {:data [:map [assets
                :context/config
                :context/graphics
                :context/screens
                [:context/vis-ui {:optional true}]
                [:context/tiled-map-renderer {:optional true}]]]})

(def-type :properties/app
  {:schema [:app/lwjgl3
            :app/context]
   :overview {:title "Apps"
              :columns 10}})
