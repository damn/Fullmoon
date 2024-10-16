(ns core.app
  (:require [clojure.gdx.app :as app]
            [clojure.gdx.assets :as assets]
            [clojure.gdx.graphics :as g :refer [white black]]
            [clojure.gdx.graphics.camera :as 🎥]
            [clojure.gdx.input :refer [key-pressed? key-just-pressed?]]
            [clojure.gdx.screen :as screen]
            [clojure.gdx.ui :as ui]
            [clojure.gdx.ui.actor :as a]
            [clojure.gdx.ui.stage-screen :as stage-screen :refer [stage-get]]
            [core.editor :as property-editor]
            core.effect.entity
            core.effect.projectile
            core.effect.spawn
            core.effect.target
            [core.db :as db]
            core.tx.gdx
            property.audiovisual
            [utils.core :refer [bind-root get-namespaces get-vars]]
            [world.core :as world]
            world.creature
            world.entity.stats
            world.generate
            world.projectile
            [world.render :refer [render-world!]]
            [world.game-loop :refer [game-loop]]
            world.setup))

(def dev-mode? (= (System/getenv "DEV_MODE") "true"))

; 28.4 viewportwidth
; 16 viewportheight
; camera shows :
;  [-viewportWidth/2, -(viewportHeight/2-1)] - [(viewportWidth/2-1), viewportHeight/2]
; zoom default '1'
; zoom 2 -> shows double amount

; we want min/max explored tiles X / Y and show the whole explored area....

(defn- minimap-zoom []
  (let [positions-explored (map first
                                (remove (fn [[position value]]
                                          (false? value))
                                        (seq @world/explored-tile-corners)))
        left   (apply min-key (fn [[x y]] x) positions-explored)
        top    (apply max-key (fn [[x y]] y) positions-explored)
        right  (apply max-key (fn [[x y]] x) positions-explored)
        bottom (apply min-key (fn [[x y]] y) positions-explored)]
    (🎥/calculate-zoom (g/world-camera)
                       :left left
                       :top top
                       :right right
                       :bottom bottom)))

(defn- ->tile-corner-color-setter [explored?]
  (fn tile-corner-color-setter [color x y]
    (if (get explored? [x y]) white black)))

#_(deftype Screen []
    (show [_]
      (🎥/set-zoom! (g/world-camera) (minimap-zoom)))

    (hide [_]
      (🎥/reset-zoom! (g/world-camera)))

    ; TODO fixme not subscreen
    (render [_]
      (g/draw-tiled-map world/tiled-map
                        (->tile-corner-color-setter @world/explored-tile-corners))
      (g/render-world-view! (fn []
                              (g/draw-filled-circle (🎥/camera-position (g/world-camera))
                                                    0.5
                                                    :green)))
      (when (or (key-just-pressed? :keys/tab)
                (key-just-pressed? :keys/escape))
        (screen/change! :screens/world))))

#_(defc :screens/minimap
  (component/create [_]
    (->Screen)))

(defn- hotkey->window-id []
  (merge {:keys/i :inventory-window
          :keys/e :entity-info-window}
         (when dev-mode?
           {:keys/z :debug-window})))

(defn- check-window-hotkeys []
  (doseq [[hotkey window-id] (hotkey->window-id)
          :when (key-just-pressed? hotkey)]
    (a/toggle-visible! (get (:windows (stage-get)) window-id))))

(defn- close-windows?! []
  (let [windows (ui/children (:windows (stage-get)))]
    (if (some a/visible? windows)
      (do
       (run! #(a/set-visible! % false) windows)
       true))))

(defn- adjust-zoom [camera by] ; DRY map editor
  (🎥/set-zoom! camera (max 0.1 (+ (🎥/zoom camera) by))))

(def ^:private zoom-speed 0.05)

(defn- check-zoom-keys []
  (let [camera (g/world-camera)]
    (when (key-pressed? :keys/minus)  (adjust-zoom camera    zoom-speed))
    (when (key-pressed? :keys/equals) (adjust-zoom camera (- zoom-speed)))))

; TODO move to actor/stage listeners ? then input processor used ....
(defn- check-key-input []
  (check-zoom-keys)
  (check-window-hotkeys)
  (cond (and (key-just-pressed? :keys/escape)
             (not (close-windows?!)))
        (screen/change! :screens/options-menu)

        ; TODO not implementing StageSubScreen so NPE no screen-render!
        #_(key-just-pressed? :keys/tab)
        #_(screen/change! :screens/minimap)))

(deftype WorldScreen []
  screen/Screen
  (screen/enter! [_])

  (screen/exit! [_]
    (g/set-cursor! :cursors/default))

  (screen/render! [_]
    (render-world!)
    (game-loop (g/delta-time))
    (check-key-input))

  (screen/dispose! [_]))

(defn- world-screen []
  [:screens/world (stage-screen/create :screen (->WorldScreen))])

(defn- start-game-fn [world-id]
  (fn []
    (screen/change! :screens/world)
    (world.setup/add-world-ctx! world-id)))

(defn- ->buttons []
  (ui/table {:rows (remove nil? (concat
                                 (for [{:keys [property/id]} (db/all :properties/worlds)]
                                   [(ui/text-button (str "Start " id) (start-game-fn id))])
                                 [(when dev-mode?
                                    [(ui/text-button "Map editor" #(screen/change! :screens/map-editor))])
                                  (when dev-mode?
                                    [(ui/text-button "Property editor" #(screen/change! :screens/property-editor))])
                                  [(ui/text-button "Exit" app/exit!)]]))
             :cell-defaults {:pad-bottom 25}
             :fill-parent? true}))

(deftype MainMenuScreen []
  screen/Screen
  (screen/enter! [_]
    (g/set-cursor! :cursors/default))
  (screen/exit! [_])
  (screen/render! [_])
  (screen/dispose! [_]))

(defn- main-menu-screen [->background-image]
  [:screens/main-menu
   (stage-screen/create :actors
                        [(->background-image)
                         (->buttons)
                         (ui/actor {:act (fn []
                                           (when (key-just-pressed? :keys/escape)
                                             (app/exit!)))})]
                        :screen (->MainMenuScreen))])

(defprotocol ^:private StatusCheckBox
  (^:private get-text [this])
  (^:private get-state [this])
  (^:private set-state [this is-selected]))

(deftype VarStatusCheckBox [^clojure.lang.Var avar]
  StatusCheckBox
  (get-text [this]
    (let [m (meta avar)]
      (str "[LIGHT_GRAY]" (str (:ns m)) "/[WHITE]" (name (:name m)) "[]")))

  (get-state [this]
    @avar)

  (set-state [this is-selected]
    (bind-root avar is-selected)))

; TODO not using clojure.gdx ns ... only core

(defn- debug-flags [] ;
  (apply concat
         ; TODO
         (for [nmspace (get-namespaces #{"core"})] ; DRY in core.component check ns-name & core.app require all ... core.components
           (get-vars nmspace (fn [avar] (:dbg-flag (meta avar)))))))

; TODO FIXME IF THE FLAGS ARE CHANGED MANUALLY IN THE REPL THIS IS NOT REFRESHED
; -. rebuild it on window open ...
(def ^:private debug-flags (map ->VarStatusCheckBox (debug-flags)))

(def ^:private key-help-text
  "[W][A][S][D] - Move\n[I] - Inventory window\n[E] - Entity Info window\n[-]/[=] - Zoom\n[TAB] - Minimap\n[P]/[SPACE] - Unpause")

(defn- create-table []
  (ui/table {:rows (concat
                    [[(ui/label key-help-text)]]
                    (when dev-mode? [[(ui/label "[Z] - Debug window")]])
                    (when dev-mode? (for [check-box debug-flags]
                                      [(ui/check-box (get-text check-box)
                                                     (partial set-state check-box)
                                                     (boolean (get-state check-box)))]))
                    [[(ui/text-button "Resume" #(screen/change! :screens/world))]
                     [(ui/text-button "Exit"   #(screen/change! :screens/main-menu))]])
             :fill-parent? true
             :cell-defaults {:pad-bottom 10}}))

(defn- options-menu-screen [->background-image]
  [:screens/options-menu
   (stage-screen/create :actors
                        [(->background-image)
                         (create-table)
                         (ui/actor {:act #(when (key-just-pressed? :keys/escape)
                                            (screen/change! :screens/world))})])])

(def ^:private image-file "images/moon_background.png")

(defn- ->background []
  (ui/image->widget (g/image image-file)
                    {:fill-parent? true
                     :scaling :fill
                     :align :center}))

(defn- screens []
  [(main-menu-screen ->background)
   (world.generate/map-editor-screen)
   (options-menu-screen ->background)
   (property-editor/screen ->background)
   (world-screen)])

(defn- start-app! [& {:keys [graphics ui] :as config}]
  (db/load! "properties.edn")
  (app/start! (reify app/Listener
                (create! [_]
                  (assets/load! "resources/")
                  (g/load! graphics)
                  (ui/load! ui)
                  (screen/set-screens! (screens)))

                (dispose! [_]
                  (assets/dispose!)
                  (g/dispose!)
                  (ui/dispose!)
                  (screen/dispose-all!))

                (render! [_]
                  (com.badlogic.gdx.utils.ScreenUtils/clear com.badlogic.gdx.graphics.Color/BLACK)
                  (screen/render! (screen/current)))

                (resize! [_ dimensions]
                  (g/resize! dimensions)))
              config))

(defn -main []
  (start-app! :title "Core"
              :width 1440
              :height 900
              :full-screen? false
              :fps 60
              :graphics {:cursors {:cursors/bag ["bag001" [0 0]]
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
                                              :tile-size 48}}}
              :ui :skin-scale/x1))
