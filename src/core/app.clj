(ns core.app
  (:require [clojure.gdx :refer :all]
            core.config
            core.creature
            core.stat
            [core.world :as world])
  (:import (com.badlogic.gdx ApplicationAdapter)
           (com.badlogic.gdx.backends.lwjgl3 Lwjgl3Application
                                             Lwjgl3ApplicationConfiguration)
           (com.badlogic.gdx.utils SharedLibraryLoader
                                   ScreenUtils)))

(def dev-mode? (= (System/getenv "DEV_MODE") "true"))

(load "screens/minimap"
      "screens/world"
      "screens/main_menu"
      "screens/options"
      "screens/property_editor")

(defn lwjgl3-app-config
  [{:keys [title width height full-screen? fps]}]
  {:pre [title width height (boolean? full-screen?) (or (nil? fps) (int? fps))]}
  ; https://github.com/libgdx/libgdx/pull/7361
  ; Maybe can delete this when using that new libgdx version
  ; which includes this PR.
  (when SharedLibraryLoader/isMac
    (.set org.lwjgl.system.Configuration/GLFW_LIBRARY_NAME "glfw_async")
    (.set org.lwjgl.system.Configuration/GLFW_CHECK_THREAD0 false))
  (let [config (doto (Lwjgl3ApplicationConfiguration.)
                 (.setTitle title)
                 (.setForegroundFPS (or fps 60)))]
    (if full-screen?
      (.setFullscreenMode config (Lwjgl3ApplicationConfiguration/getDisplayMode))
      (.setWindowedMode config width height))
    config))

(def graphics {:cursors {:cursors/bag ["bag001" [0 0]]
                         :cursors/black-x ["black_x" [0 0]]
                         :cursors/default ["default" [0 0]]
                         :cursors/denied ["denied" [16 16]]
                         :cursors/hand-before-grab ["hand004" [4 16]]
                         :cursors/hand-before-grab-gray ["hand004_gray" [4 16]]
                         :cursors/hand-grab ["hand003" [4 16]]
                         :cursors/move-window ["move002" [16 16]]
                         :cursors/no-skill-selected ["denied003" [0 0]]
                         :cursors/over-button ["hand002" [0 0]]
                         :cursors/sandclock ["sandclock" [16 16]]
                         :cursors/skill-not-usable ["x007" [0 0]]
                         :cursors/use-skill ["pointer004" [0 0]]
                         :cursors/walking ["walking" [16 16]]}
               :default-font {:file "fonts/exocet/films.EXL_____.ttf"
                              :size 16
                              :quality-scaling 2}
               :views {:gui-view {:world-width 1440
                                  :world-height 900}
                       :world-view {:world-width 1440
                                    :world-height 900
                                    :tile-size 48}}})

(def lwjgl3-config {:title "Core"
                    :width 1440
                    :height 900
                    :full-screen? false
                    :fps 60})

; assets,g,screens lifecyclelistener?

(defn -main []
  (load-properties-db! "resources/properties.edn")
  (Lwjgl3Application. (proxy [ApplicationAdapter] []
                        (create []
                          ; (load! assets "resources/")
                          (load-assets! "resources/")
                          (load-graphics! graphics)
                          (load-ui! :skin-scale/x1)
                          (load-screens! [:screens/main-menu
                                          :screens/map-editor
                                          :screens/options-menu
                                          :screens/property-editor
                                          :screens/world]))

                        (dispose []
                          (dispose! assets)
                          (dispose-graphics!)
                          (dispose-ui!)
                          (dispose-screens!))

                        (render []
                          (ScreenUtils/clear black)
                          (screen-render! (current-screen)))

                        (resize [w h]
                          (update-viewports! [w h])))
                      (lwjgl3-app-config lwjgl3-config)))
