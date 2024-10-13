(ns core.app
  (:require [clojure.gdx :refer :all]
            [clojure.gdx.app :as app]
            [clojure.gdx.audio :refer [play-sound!]]
            [clojure.gdx.assets :as assets]
            [clojure.gdx.graphics :as g :refer [white black]]
            [clojure.gdx.graphics.camera :as 🎥]
            [clojure.gdx.input :refer [key-pressed? key-just-pressed?]]
            [clojure.gdx.screen :as screen]
            [clojure.gdx.tiled :as t]
            [clojure.gdx.ui :as ui]
            [clojure.gdx.ui.actor :as a]
            [clojure.gdx.ui.stage :as stage]
            [clojure.gdx.ui.stage-screen :as stage-screen :refer [stage-get mouse-on-actor?]]
            [clojure.gdx.utils :refer [dispose!]]
            [clojure.gdx.math.shape :as shape]
            [core.component :refer [defc do! effect!] :as component]
            [core.editor :as property-editor]
            [core.db :as db]
            [core.val-max :as val-max]
            [core.widgets.error :refer [error-window!]]
            [world.generate :as world]
            [data.grid2d :as g2d]
            property.audiovisual
            [utils.core :refer [bind-root ->tile tile->middle readable-number get-namespaces get-vars]]
            [world.content-grid :as content-grid]
            world.creature
            world.creature.states
            [world.entity :as entity]
            [world.entity.inventory :refer [->inventory-window ->inventory-window-data]]
            [world.entity.skills :refer [->action-bar ->action-bar-button-group]]
            [world.entity.state :as entity-state]
            world.entity.stats
            [world.mouseover-entity :refer [mouseover-entity*] :as mouseover-entity]
            [world.grid :as grid :refer [world-grid]]
            [world.player :refer [world-player]]
            world.projectile
            [world.potential-fields :as potential-fields]
            [world.raycaster :as raycaster]
            [world.time :refer [elapsed-time]]
            [world.widgets :refer [world-widgets]]))

(defc :tx/sound
  {:data :sound}
  (do! [[_ file]]
    (play-sound! file)
    nil))

(declare world-paused?)

(defn- render-infostr-on-bar [infostr x y h]
  (g/draw-text {:text infostr
                :x (+ x 75)
                :y (+ y 2)
                :up? true}))

(defn- ->hp-mana-bars []
  (let [rahmen      (g/image "images/rahmen.png")
        hpcontent   (g/image "images/hp.png")
        manacontent (g/image "images/mana.png")
        x (/ (g/gui-viewport-width) 2)
        [rahmenw rahmenh] (:pixel-dimensions rahmen)
        y-mana 80 ; action-bar-icon-size
        y-hp (+ y-mana rahmenh)
        render-hpmana-bar (fn [x y contentimg minmaxval name]
                            (g/draw-image rahmen [x y])
                            (g/draw-image (g/sub-image contentimg [0 0 (* rahmenw (val-max/ratio minmaxval)) rahmenh])
                                          [x y])
                            (render-infostr-on-bar (str (readable-number (minmaxval 0)) "/" (minmaxval 1) " " name) x y rahmenh))]
    (ui/actor {:draw (fn []
                       (let [player-entity* @world-player
                             x (- x (/ rahmenw 2))]
                         (render-hpmana-bar x y-hp   hpcontent   (entity-stat player-entity* :stats/hp) "HP")
                         (render-hpmana-bar x y-mana manacontent (entity-stat player-entity* :stats/mana) "MP")))})))

(defn- skill-info [{:keys [entity/skills]}]
  (clojure.string/join "\n"
                       (for [{:keys [property/id skill/cooling-down?]} (vals skills)
                             :when cooling-down? ]
                         [id [:cooling-down? (boolean cooling-down?)]])))

(defn- debug-infos ^String []
  (let [world-mouse (g/world-mouse-position)]
    (str
     "logic-frame: " world.time/logic-frame "\n"
     "FPS: " (g/frames-per-second)  "\n"
     "Zoom: " (🎥/zoom (g/world-camera)) "\n"
     "World: "(mapv int world-mouse) "\n"
     "X:" (world-mouse 0) "\n"
     "Y:" (world-mouse 1) "\n"
     "GUI: " (g/gui-mouse-position) "\n"
     "paused? " world-paused? "\n"
     "elapsed-time " (readable-number elapsed-time) " seconds \n"
     "skill cooldowns: " (skill-info @world-player) "\n"
     (when-let [entity* (mouseover-entity*)]
       (str "Mouseover-entity uid: " (:entity/uid entity*)))
     ;"\nMouseover-Actor:\n"
     #_(when-let [actor (mouse-on-actor?)]
         (str "TRUE - name:" (.getName actor)
              "id: " (a/id actor))))))

(defn- ->debug-window []
  (let [label (ui/label "")
        window (ui/window {:title "Debug"
                           :id :debug-window
                           :visible? false
                           :position [0 (g/gui-viewport-height)]
                           :rows [[label]]})]
    (ui/add-actor! window (ui/actor {:act #(do
                                            (.setText label (debug-infos))
                                            (.pack window))}))
    window))

(def ^:private disallowed-keys [:entity/skills
                                :entity/state
                                :entity/faction
                                :active-skill])

(defn- ->entity-info-window []
  (let [label (ui/label "")
        window (ui/window {:title "Info"
                           :id :entity-info-window
                           :visible? false
                           :position [(g/gui-viewport-width) 0]
                           :rows [[{:actor label :expand? true}]]})]
    ; TODO do not change window size ... -> no need to invalidate layout, set the whole stage up again
    ; => fix size somehow.
    (ui/add-actor! window (ui/actor {:act (fn update-label-text []
                                            ; items then have 2x pretty-name
                                            #_(.setText (.getTitleLabel window)
                                                        (if-let [entity* (mouseover-entity*)]
                                                          (component/info-text [:property/pretty-name (:property/pretty-name entity*)])
                                                          "Entity Info"))
                                            (.setText label
                                                      (str (when-let [entity* (mouseover-entity*)]
                                                             (component/info-text
                                                              ; don't use select-keys as it loses Entity record type
                                                              (apply dissoc entity* disallowed-keys)))))
                                            (.pack window))}))
    window))

(def ^:private message-to-player nil)

(def ^:private duration-seconds 1.5)

(defn- draw-player-message []
  (when-let [{:keys [message]} message-to-player]
    (g/draw-text {:x (/ (g/gui-viewport-width) 2)
                  :y (+ (/ (g/gui-viewport-height) 2) 200)
                  :text message
                  :scale 2.5
                  :up? true})))

(defn- check-remove-message []
  (when-let [{:keys [counter]} message-to-player]
    (alter-var-root #'message-to-player update :counter + (g/delta-time))
    (when (>= counter duration-seconds)
      (bind-root #'message-to-player nil))))

(defc :widgets/player-message
  (component/create [_]
    (ui/actor {:draw draw-player-message
               :act check-remove-message})))

(defc :tx/msg-to-player
  (do! [[_ message]]
    (bind-root #'message-to-player {:message message :counter 0})
    nil))

(defn- ->ui-actors [widget-data]
  [(ui/table {:rows [[{:actor (->action-bar)
                       :expand? true
                       :bottom? true}]]
              :id :action-bar-table
              :cell-defaults {:pad 2}
              :fill-parent? true})
   (->hp-mana-bars)
   (ui/group {:id :windows
              :actors [(->debug-window)
                       (->entity-info-window)
                       (->inventory-window widget-data)]})
   (ui/actor {:draw world.creature.states/draw-item-on-cursor})
   (component/create [:widgets/player-message])])

(defn ->world-widgets []
  (let [widget-data {:action-bar (->action-bar-button-group)
                     :slot->background (->inventory-window-data)}
        stage (stage-get)]
    (stage/clear! stage)
    (run! #(stage/add! stage %) (->ui-actors widget-data))
    widget-data))

(declare explored-tile-corners)

(defn- init-explored-tile-corners! [width height]
  (.bindRoot #'explored-tile-corners (atom (g2d/create-grid width height (constantly false)))))

(defn- world-grid-position->value-fn [tiled-map]
  (fn [position]
    (case (t/movement-property tiled-map position)
      "none" :none
      "air"  :air
      "all"  :all)))

(declare entity-tick-error)

(declare world-tiled-map)

(defn- cleanup-last-world! []
  (when (bound? #'world-tiled-map)
    (dispose! world-tiled-map)))

(def ^:private ^:dbg-flag spawn-enemies? true)

(def ^:private player-components {:entity/state [:state/player :player-idle]
                                  :entity/faction :good
                                  :entity/player? true
                                  :entity/free-skill-points 3
                                  :entity/clickable {:type :clickable/player}
                                  :entity/click-distance-tiles 1.5})

(def ^:private npc-components {:entity/state [:state/npc :npc-sleeping]
                               :entity/faction :evil})

; player-creature needs mana & inventory
; till then hardcode :creatures/vampire
(defn- world->player-creature [start-position]
  {:position start-position
   :creature-id :creatures/vampire
   :components player-components})

(defn- world->enemy-creatures [tiled-map]
  (for [[position creature-id] (t/positions-with-property tiled-map :creatures :id)]
    {:position position
     :creature-id (keyword creature-id)
     :components npc-components}))

(defn spawn-creatures! [tiled-map start-position]
  (effect! (for [creature (cons (world->player-creature start-position)
                                (when spawn-enemies?
                                  (world->enemy-creatures tiled-map)))]
             [:tx/creature (update creature :position tile->middle)])))

(defn- init-new-world! [{:keys [tiled-map start-position]}]
  (bind-root #'entity-tick-error nil)
  (world.time/init!)
  (bind-root #'world-widgets (->world-widgets))
  (entity/init-uids-entities!)

  (bind-root #'world-tiled-map tiled-map)
  (let [w (t/width  tiled-map)
        h (t/height tiled-map)
        grid (world.grid/init! w h (world-grid-position->value-fn tiled-map))]
    (raycaster/init! grid grid/blocks-vision?)
    (content-grid/init! :cell-size 16 :width w :height h)
    (init-explored-tile-corners! w h))

  (spawn-creatures! tiled-map start-position))

; TODO  add-to-world/assoc/dissoc uid from entity move together here
; also core.screens/world ....
(defn- add-world-ctx [world-property-id]
  (cleanup-last-world!)
  (init-new-world! (world/generate-level world-property-id)))

(defc :tx/add-to-world
  (do! [[_ entity]]
    (content-grid/update-entity! entity)
    ; https://github.com/damn/core/issues/58
    ;(assert (valid-position? grid @entity)) ; TODO deactivate because projectile no left-bottom remove that field or update properly for all
    (grid/add-entity! entity)
    nil))

(defc :tx/remove-from-world
  (do! [[_ entity]]
    (content-grid/remove-entity! entity)
    (grid/remove-entity! entity)
    nil))

(defc :tx/position-changed
  (do! [[_ entity]]
    (content-grid/update-entity! entity)
    (grid/entity-position-changed! entity)
    nil))

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
      (g/draw-tiled-map world-tiled-map
                        (->tile-corner-color-setter @explored-tile-corners))
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

(defn- geom-test []
  (let [position (g/world-mouse-position)
        grid world-grid
        radius 0.8
        circle {:position position :radius radius}]
    (g/draw-circle position radius [1 0 0 0.5])
    (doseq [[x y] (map #(:position @%) (grid/circle->cells grid circle))]
      (g/draw-rectangle x y 1 1 [1 0 0 0.5]))
    (let [{[x y] :left-bottom :keys [width height]} (shape/circle->outer-rectangle circle)]
      (g/draw-rectangle x y width height [0 0 1 1]))))

(def ^:private ^:dbg-flag tile-grid? false)
(def ^:private ^:dbg-flag potential-field-colors? false)
(def ^:private ^:dbg-flag cell-entities? false)
(def ^:private ^:dbg-flag cell-occupied? false)

(defn- tile-debug []
  (let [grid world-grid
        🎥 (g/world-camera)
        [left-x right-x bottom-y top-y] (🎥/frustum 🎥)]

    (when tile-grid?
      (g/draw-grid (int left-x) (int bottom-y)
                   (inc (int (g/world-viewport-width)))
                   (+ 2 (int (g/world-viewport-height)))
                   1 1 [1 1 1 0.8]))

    (doseq [[x y] (🎥/visible-tiles 🎥)
            :let [cell (grid [x y])]
            :when cell
            :let [cell* @cell]]

      (when (and cell-entities? (seq (:entities cell*)))
        (g/draw-filled-rectangle x y 1 1 [1 0 0 0.6]))

      (when (and cell-occupied? (seq (:occupied cell*)))
        (g/draw-filled-rectangle x y 1 1 [0 0 1 0.6]))

      (when potential-field-colors?
        (let [faction :good
              {:keys [distance entity]} (faction cell*)]
          (when distance
            (let [ratio (/ distance (potential-fields/factions-iterations faction))]
              (g/draw-filled-rectangle x y 1 1 [ratio (- 1 ratio) ratio 0.6]))))))))

(def ^:private ^:dbg-flag highlight-blocked-cell? true)

(defn- highlight-mouseover-tile []
  (when highlight-blocked-cell?
    (let [[x y] (->tile (g/world-mouse-position))
          cell (get world-grid [x y])]
      (when (and cell (#{:air :none} (:movement @cell)))
        (g/draw-rectangle x y 1 1
                          (case (:movement @cell)
                            :air  [1 1 0 0.5]
                            :none [1 0 0 0.5]))))))

(defn- before-entities [] (tile-debug))

(defn- after-entities []
  #_(geom-test)
  (highlight-mouseover-tile))

(def ^:private ^:dbg-flag pausing? true)

(defn- player-state-pause-game? [] (pause-game? (entity-state/state-obj @world-player)))
(defn- player-update-state      [] (manual-tick (entity-state/state-obj @world-player)))

(defn- player-unpaused? []
  (or (key-just-pressed? :keys/p)
      (key-pressed? :keys/space))) ; FIXMe :keys? shouldnt it be just :space?

(defn- update-game-paused []
  (bind-root #'world-paused? (or entity-tick-error
                                 (and pausing?
                                      (player-state-pause-game?)
                                      (not (player-unpaused?)))))
  nil)

(defn- update-world []
  (world.time/update! (min (g/delta-time) entity/max-delta-time))
  (let [entities (content-grid/active-entities)]
    (potential-fields/update! entities)
    (try (entity/tick-entities! entities)
         (catch Throwable t
           (error-window! t)
           (bind-root #'entity-tick-error t))))
  nil)

(defn- game-loop []
  (effect! [player-update-state
            mouseover-entity/update! ; this do always so can get debug info even when game not running
            update-game-paused
            #(when-not world-paused?
               (update-world))
            entity/remove-destroyed-entities! ; do not pause this as for example pickup item, should be destroyed.
            ]))

(def ^:private explored-tile-color (g/->color 0.5 0.5 0.5 1))

(def ^:private ^:dbg-flag see-all-tiles? false)

(comment
 (def ^:private count-rays? false)

 (def ray-positions (atom []))
 (def do-once (atom true))

 (count @ray-positions)
 2256
 (count (distinct @ray-positions))
 608
 (* 608 4)
 2432
 )

(defn- ->tile-color-setter [light-cache light-position explored-tile-corners]
  (fn tile-color-setter [_color x y]
    (let [position [(int x) (int y)]
          explored? (get @explored-tile-corners position) ; TODO needs int call ?
          base-color (if explored? explored-tile-color black)
          cache-entry (get @light-cache position :not-found)
          blocked? (if (= cache-entry :not-found)
                     (let [blocked? (raycaster/ray-blocked? light-position position)]
                       (swap! light-cache assoc position blocked?)
                       blocked?)
                     cache-entry)]
      #_(when @do-once
          (swap! ray-positions conj position))
      (if blocked?
        (if see-all-tiles? white base-color)
        (do (when-not explored?
              (swap! explored-tile-corners assoc (->tile position) true))
            white)))))

(defn render-map [light-position]
  (g/draw-tiled-map world-tiled-map
                    (->tile-color-setter (atom nil)
                                         light-position
                                         explored-tile-corners))
  #_(reset! do-once false))

(defn- render-world! []
  (🎥/set-position! (g/world-camera) (:position @world-player))
  (render-map (🎥/position (g/world-camera)))
  (g/render-world-view! (fn []
                          (before-entities)
                          (entity/render-entities! (map deref (content-grid/active-entities)))
                          (after-entities))))

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
    (game-loop)
    (check-key-input))

  (screen/dispose! [_]))

(defn- world-screen []
  [:screens/world (stage-screen/create :screen (->WorldScreen))])

(defn- start-game-fn [world-id]
  (fn []
    (screen/change! :screens/world)
    (add-world-ctx world-id)))

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
   (world/map-editor-screen)
   (options-menu-screen ->background)
   (property-editor/screen ->background)
   (world-screen)])

(defn- start-app! [& {:keys [resources properties graphics screen-ks ui] :as config}]
  (db/load! properties)
  (app/start! (reify app/Listener
                (create! [_]
                  (assets/load! resources)
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
              :resources "resources/"
              :properties "resources/properties.edn"
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
