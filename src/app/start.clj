(ns ^:no-doc app.start
  (:require [app.screens.map-editor :as map-editor]
            [app.screens.options :as options-screen]
            [component.db :as db]
            [component.tx :as tx]
            [editor.visui :as property-editor]
            [gdx.app :as app]
            [gdx.assets :as assets]
            [gdx.graphics :as g :refer [white black]]
            [gdx.graphics.camera :as 🎥]
            [gdx.input :refer [key-pressed? key-just-pressed?]]
            [gdx.screen :as screen]
            [gdx.ui :as ui]
            [gdx.ui.actor :as a]
            [gdx.ui.stage :as stage]
            [gdx.ui.stage-screen :as stage-screen :refer [stage-get]]
            [gdx.vis-ui :as vis-ui]
            level.generate
            [utils.core :refer [sort-by-order dev-mode?]]
            moon.audiovisual
            [moon.creature :as creature]
            moon.creature.player.item-on-cursor
            [moon.item :refer [->inventory-window]]
            [moon.skill :refer [action-bar]]
            moon.projectile
            [world.core :as world]
            world.entity.animation
            world.entity.delete-after-duration
            world.entity.image
            world.entity.line
            world.entity.movement
            world.entity.string-effect
            world.effect.entity
            world.effect.target
            [world.entity :as entity]
            world.entity.stats
            [moon.widgets.debug-window :as debug-window]
            [moon.widgets.entity-info-window :as entity-info-window]
            [moon.widgets.hp-mana :as hp-mana-bars]
            [moon.widgets.player-message :as player-message]
            moon.widgets.player-modal))

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
    (world/tick!)
    (check-key-input))

  (screen/dispose! [_]))

(defn- world-screen []
  [:screens/world (stage-screen/create :screen (->WorldScreen))])

(defn- world-actors []
  [(ui/table {:rows [[{:actor (action-bar)
                       :expand? true
                       :bottom? true}]]
              :id :action-bar-table
              :cell-defaults {:pad 2}
              :fill-parent? true})
   (hp-mana-bars/create)
   (ui/group {:id :windows
              :actors [(debug-window/create)
                       (entity-info-window/create)
                       (->inventory-window)]})
   (ui/actor {:draw moon.creature.player.item-on-cursor/draw-item-on-cursor})
   (player-message/create)])

(defn- reset-stage! []
  (let [stage (stage-get)] ; these fns to stage itself
    (stage/clear! stage)
    (run! #(stage/add! stage %) (world-actors))))

(defn- start-game-fn [world-id]
  (fn []
    (screen/change! :screens/world)
    (reset-stage!)
    (let [level (level.generate/generate-level world-id)]
      (world/init! (:tiled-map level))
      (creature/spawn-all level))))

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

(def ^:private image-file "images/moon_background.png")

(defn- ->background []
  (ui/image->widget (g/image image-file)
                    {:fill-parent? true
                     :scaling :fill
                     :align :center}))

(defn- screens []
  [(main-menu-screen ->background)
   (map-editor/create)
   (options-screen/create ->background)
   (property-editor/screen ->background)
   (world-screen)])

(defn- start-app! [& {:keys [graphics] :as config}]
  (db/load! "properties.edn")
  (app/start! (proxy [com.badlogic.gdx.ApplicationAdapter] []
                (create []
                  (assets/load! "resources/")
                  (g/load! graphics)
                  (vis-ui/load! :skin-scale/x1)
                  (screen/set-screens! (screens)))

                (dispose []
                  (assets/dispose!)
                  (g/dispose!)
                  (vis-ui/dispose!)
                  (screen/dispose-all!))

                (render []
                  (com.badlogic.gdx.utils.ScreenUtils/clear com.badlogic.gdx.graphics.Color/BLACK)
                  (screen/render! (screen/current)))

                (resize [w h]
                  (g/resize! [w h])))
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
                                              :tile-size 48}}}))
