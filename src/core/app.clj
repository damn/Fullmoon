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

(load "editor")

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
                                        (seq @explored-tile-corners)))
        left   (apply min-key (fn [[x y]] x) positions-explored)
        top    (apply max-key (fn [[x y]] y) positions-explored)
        right  (apply max-key (fn [[x y]] x) positions-explored)
        bottom (apply min-key (fn [[x y]] y) positions-explored)]
    (calculate-zoom (world-camera)
                    :left left
                    :top top
                    :right right
                    :bottom bottom)))

(defn- ->tile-corner-color-setter [explored?]
  (fn tile-corner-color-setter [color x y]
    (if (get explored? [x y]) white black)))

#_(deftype Screen []
    (show [_]
      (set-zoom! (world-camera) (minimap-zoom)))

    (hide [_]
      (reset-zoom! (world-camera)))

    ; TODO fixme not subscreen
    (render [_]
      (draw-tiled-map world-tiled-map
                      (->tile-corner-color-setter @explored-tile-corners))
      (render-world-view! (fn []
                            (draw-filled-circle (camera-position (world-camera))
                                                0.5
                                                :green)))
      (when (or (key-just-pressed? :keys/tab)
                (key-just-pressed? :keys/escape))
        (change-screen :screens/world))))

#_(defc :screens/minimap
  (->mk [_]
    (->Screen)))

(defn- geom-test []
  (let [position (world-mouse-position)
        grid world-grid
        radius 0.8
        circle {:position position :radius radius}]
    (draw-circle position radius [1 0 0 0.5])
    (doseq [[x y] (map #(:position @%) (circle->cells grid circle))]
      (draw-rectangle x y 1 1 [1 0 0 0.5]))
    (let [{[x y] :left-bottom :keys [width height]} (circle->outer-rectangle circle)]
      (draw-rectangle x y width height [0 0 1 1]))))

(def ^:private ^:dbg-flag tile-grid? false)
(def ^:private ^:dbg-flag potential-field-colors? false)
(def ^:private ^:dbg-flag cell-entities? false)
(def ^:private ^:dbg-flag cell-occupied? false)

(defn- tile-debug []
  (let [grid world-grid
        world-camera (world-camera)
        [left-x right-x bottom-y top-y] (frustum world-camera)]

    (when tile-grid?
      (draw-grid (int left-x) (int bottom-y)
                 (inc (int (world-viewport-width)))
                 (+ 2 (int (world-viewport-height)))
                 1 1 [1 1 1 0.8]))

    (doseq [[x y] (visible-tiles world-camera)
            :let [cell (grid [x y])]
            :when cell
            :let [cell* @cell]]

      (when (and cell-entities? (seq (:entities cell*)))
        (draw-filled-rectangle x y 1 1 [1 0 0 0.6]))

      (when (and cell-occupied? (seq (:occupied cell*)))
        (draw-filled-rectangle x y 1 1 [0 0 1 0.6]))

      (when potential-field-colors?
        (let [faction :good
              {:keys [distance entity]} (faction cell*)]
          (when distance
            (let [ratio (/ distance (factions-iterations faction))]
              (draw-filled-rectangle x y 1 1 [ratio (- 1 ratio) ratio 0.6]))))))))

(def ^:private ^:dbg-flag highlight-blocked-cell? true)

(defn- highlight-mouseover-tile []
  (when highlight-blocked-cell?
    (let [[x y] (->tile (world-mouse-position))
          cell (get world-grid [x y])]
      (when (and cell (#{:air :none} (:movement @cell)))
        (draw-rectangle x y 1 1
                        (case (:movement @cell)
                          :air  [1 1 0 0.5]
                          :none [1 0 0 0.5]))))))

(defn- before-entities [] (tile-debug))

(defn- after-entities []
  #_(geom-test)
  (highlight-mouseover-tile))

(defn- calculate-mouseover-entity []
  (let [player-entity* @player-entity
        hits (remove #(= (:z-order %) :z-order/effect) ; or: only items/creatures/projectiles.
                     (map deref
                          (point->entities (world-mouse-position))))]
    (->> render-order
         (sort-by-order hits :z-order)
         reverse
         (filter #(line-of-sight? player-entity* %))
         first
         :entity/id)))

(defn- update-mouseover-entity []
  (let [entity (if (mouse-on-actor?)
                 nil
                 (calculate-mouseover-entity))]
    [(when-let [old-entity mouseover-entity]
       [:e/dissoc old-entity :entity/mouseover?])
     (when entity
       [:e/assoc entity :entity/mouseover? true])
     (fn []
       (bind-root #'mouseover-entity entity)
       nil)]))

(def ^:private ^:dbg-flag pausing? true)

(defn- player-unpaused? []
  (or (key-just-pressed? :keys/p)
      (key-pressed? :keys/space)))

(defn- update-game-paused []
  (bind-root #'world-paused? (or world/entity-tick-error
                                 (and pausing?
                                      (player-state-pause-game?)
                                      (not (player-unpaused?)))))
  nil)

(defn- update-world []
  (world/update-time (min (delta-time) max-delta-time))
  (let [entities (active-entities)]
    (potential-fields-update! entities)
    (try (tick-entities! entities)
         (catch Throwable t
           (error-window! t)
           (bind-root #'world/entity-tick-error t))))
  nil)

(defn- game-loop []
  (effect! [player-update-state
            update-mouseover-entity ; this do always so can get debug info even when game not running
            update-game-paused
            #(when-not world-paused?
               (update-world))
            remove-destroyed-entities! ; do not pause this as for example pickup item, should be destroyed.
            ]))

(defn- render-world! []
  (camera-set-position! (world-camera) (:position @player-entity))
  (world/render-map (camera-position (world-camera)))
  (render-world-view! (fn []
                        (before-entities)
                        (render-entities! (map deref (active-entities)))
                        (after-entities))))

(defn- hotkey->window-id []
  (merge {:keys/i :inventory-window
          :keys/e :entity-info-window}
         (when dev-mode?
           {:keys/z :debug-window})))

(defn- check-window-hotkeys []
  (doseq [[hotkey window-id] (hotkey->window-id)
          :when (key-just-pressed? hotkey)]
    (toggle-visible! (get (:windows (stage-get)) window-id))))

(defn- close-windows?! []
  (let [windows (children (:windows (stage-get)))]
    (if (some visible? windows)
      (do
       (run! #(set-visible! % false) windows)
       true))))

(defn- adjust-zoom [camera by] ; DRY map editor
  (set-zoom! camera (max 0.1 (+ (zoom camera) by))))

(def ^:private zoom-speed 0.05)

(defn- check-zoom-keys []
  (let [camera (world-camera)]
    (when (key-pressed? :keys/minus)  (adjust-zoom camera    zoom-speed))
    (when (key-pressed? :keys/equals) (adjust-zoom camera (- zoom-speed)))))

; TODO move to actor/stage listeners ? then input processor used ....
(defn- check-key-input []
  (check-zoom-keys)
  (check-window-hotkeys)
  (cond (and (key-just-pressed? :keys/escape)
             (not (close-windows?!)))
        (change-screen :screens/options-menu)

        ; TODO not implementing StageSubScreen so NPE no screen-render!
        #_(key-just-pressed? :keys/tab)
        #_(change-screen :screens/minimap)))

(defc :world/sub-screen
  (screen-exit [_]
    (set-cursor! :cursors/default))

  (screen-render [_]
    (render-world!)
    (game-loop)
    (check-key-input)))

(derive :screens/world :screens/stage)
(defc :screens/world
  (->mk [_]
    {:stage (->stage [])
     :sub-screen [:world/sub-screen]}))

(defn- start-game-fn [world-id]
  (fn []
    (change-screen :screens/world)
    (add-world-ctx world-id)))

(defn- ->buttons []
  (->table {:rows (remove nil? (concat
                                   (for [{:keys [property/id]} (all-properties :properties/worlds)]
                                     [(->text-button (str "Start " id) (start-game-fn id))])
                                   [(when dev-mode?
                                      [(->text-button "Map editor" #(change-screen :screens/map-editor))])
                                    (when dev-mode?
                                      [(->text-button "Property editor" #(change-screen :screens/property-editor))])
                                    [(->text-button "Exit" exit-app!)]]))
               :cell-defaults {:pad-bottom 25}
               :fill-parent? true}))

(defc :main/sub-screen
  (screen-enter [_]
    (set-cursor! :cursors/default)))

(defn- ->actors []
  [(->background-image)
   (->buttons)
   (->actor {:act (fn []
                    (when (key-just-pressed? :keys/escape)
                      (exit-app!)))})])

(derive :screens/main-menu :screens/stage)
(defc :screens/main-menu
  (->mk [[k _]]
    {:sub-screen [:main/sub-screen]
     :stage (->stage (->actors))}))

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
  (->table {:rows (concat
                   [[(->label key-help-text)]]
                   (when dev-mode? [[(->label "[Z] - Debug window")]])
                   (when dev-mode? (for [check-box debug-flags]
                                     [(->check-box (get-text check-box)
                                                   (partial set-state check-box)
                                                   (boolean (get-state check-box)))]))
                   [[(->text-button "Resume" #(change-screen :screens/world))]
                    [(->text-button "Exit"   #(change-screen :screens/main-menu))]])
            :fill-parent? true
            :cell-defaults {:pad-bottom 10}}))

(defc :options/sub-screen
  (screen-render [_]
    (when (key-just-pressed? :keys/escape)
      (change-screen :screens/world))))

(derive :screens/options-menu :screens/stage)
(defc :screens/options-menu
  (->mk [_]
    {:stage (->stage [(->background-image)
                      (create-table)])
     :sub-screen [:options/sub-screen]}))

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
