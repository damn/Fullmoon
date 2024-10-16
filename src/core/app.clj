(ns core.app
  (:require [clj-commons.pretty.repl :refer [pretty-pst]]
            [clojure.gdx.app :as app]
            [clojure.gdx.assets :as assets]
            [clojure.gdx.graphics :as g :refer [white black]]
            [clojure.gdx.graphics.camera :as 🎥]
            [clojure.gdx.input :refer [key-pressed? key-just-pressed?]]
            [clojure.gdx.screen :as screen]
            [clojure.gdx.ui :as ui]
            [clojure.gdx.ui.actor :as a]
            [clojure.gdx.ui.stage :as stage]
            [clojure.gdx.ui.stage-screen :as stage-screen :refer [stage-get]]
            [core.editor :as property-editor]
            core.effect.entity
            core.effect.projectile
            core.effect.spawn
            core.effect.target
            [core.db :as db]
            [core.tx :as tx]
            core.tx.gdx
            [core.widgets.debug-window :as debug-window]
            [core.widgets.error :refer [error-window!]]
            [core.widgets.entity-info-window :as entity-info-window]
            [core.widgets.hp-mana :as hp-mana-bars]
            [core.widgets.player-message :as player-message]
            [world.entity :as entity]
            world.entity.animation
            world.entity.delete-after-duration
            world.entity.image
            [world.entity.inventory :refer [->inventory-window]]
            world.entity.line
            world.entity.movement
            [world.entity.state :as entity-state]
            world.entity.string-effect
            [world.entity.skills :refer [action-bar]]
            property.audiovisual
            [utils.core :refer [bind-root get-namespaces get-vars sort-by-order]]
            [world.core :as world]
            world.creature
            world.creature.states
            [world.entity :as entity]
            world.entity.stats
            world.generate
            [world.mouseover-entity :as mouseover-entity]
            [world.potential-fields :as potential-fields]
            world.projectile))

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

(def ^:private ^:dbg-flag pausing? true)

(defn- player-state-pause-game? [] (entity-state/pause-game? (entity-state/state-obj @world/player)))
(defn- player-update-state      [] (entity-state/manual-tick (entity-state/state-obj @world/player)))

(defn- player-unpaused? []
  (or (key-just-pressed? :keys/p)
      (key-pressed? :keys/space))) ; FIXMe :keys? shouldnt it be just :space?

(defn- update-game-paused []
  (bind-root #'world/paused? (or world/entity-tick-error
                                 (and pausing?
                                      (player-state-pause-game?)
                                      (not (player-unpaused?)))))
  nil)

(def ^:private ^:dbg-flag show-body-bounds false)

(defn- draw-body-rect [entity color]
  (let [[x y] (:left-bottom entity)]
    (g/draw-rectangle x y (:width entity) (:height entity) color)))

(defn- render-entity! [system entity]
  (try
   (when show-body-bounds
     (draw-body-rect entity (if (:collides? entity) :white :gray)))
   (run! #(system % entity) entity)
   (catch Throwable t
     (draw-body-rect entity :red)
     (pretty-pst t 12))))

(defn- render-entities!
  "Draws entities in the correct z-order and in the order of render-systems for each z-order."
  [entities]
  (let [player-entity @world/player]
    (doseq [[z-order entities] (sort-by-order (group-by :z-order entities)
                                               first
                                               entity/render-order)
            system entity/render-systems
            entity entities
            :when (or (= z-order :z-order/effect)
                      (world/line-of-sight? player-entity entity))]
      (render-entity! system entity))))

; precaution in case a component gets removed by another component
; the question is do we still want to update nil components ?
; should be contains? check ?
; but then the 'order' is important? in such case dependent components
; should be moved together?
(defn- tick-system [eid]
  (try
   (doseq [k (keys @eid)]
     (when-let [v (k @eid)]
       (tx/do-all (entity/tick [k v] eid))))
   (catch Throwable t
     (throw (ex-info "" (select-keys @eid [:entity/id]) t)))))

(defn- tick-entities!
  "Calls tick system on all components of entities."
  [entities]
  (run! tick-system entities))

(deftype WorldScreen []
  screen/Screen
  (screen/enter! [_])

  (screen/exit! [_]
    (g/set-cursor! :cursors/default))

  (screen/render! [_]
    (🎥/set-position! (g/world-camera) (:position @world/player))
    (world/render-tiled-map! (🎥/position (g/world-camera)))
    (g/render-world-view! (fn []
                            (world/render-before-entities)

                            ; this one also w. player los ...
                            (render-entities! (map deref (world/active-entities)))

                            (world/render-after-entities)))
    (tx/do-all [player-update-state
                mouseover-entity/update! ; this do always so can get debug info even when game not running
                update-game-paused
                #(when-not world/paused?
                   (world/update-time! (min (g/delta-time) entity/max-delta-time))
                   (let [entities (world/active-entities)]
                     (potential-fields/update! entities)
                     (try (run! tick-system entities)
                          (catch Throwable t
                            (error-window! t)
                            (bind-root #'world/entity-tick-error t))))
                   nil)
                world/remove-destroyed-entities! ; do not pause this as for example pickup item, should be destroyed.
                ])
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
   (ui/actor {:draw world.creature.states/draw-item-on-cursor})
   (player-message/create)])

(defn reset-stage! []
  (let [stage (stage-get)] ; these fns to stage itself
    (stage/clear! stage)
    (run! #(stage/add! stage %) (world-actors))))

(defn- start-game-fn [world-id]
  (fn []
    (screen/change! :screens/world)
    (reset-stage!)
    (world/init! (world.generate/generate-level world-id))))

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
