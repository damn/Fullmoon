(ns core.app
  (:require [clojure.world :refer :all]
            [core.item :as inventory]
            core.audiovisual
            core.creature
            core.projectile
            core.skill
            [core.world :as world])
  (:import org.lwjgl.system.Configuration
           (com.badlogic.gdx ApplicationAdapter Input$Keys)
           (com.badlogic.gdx.backends.lwjgl3 Lwjgl3Application Lwjgl3ApplicationConfiguration)
           com.badlogic.gdx.graphics.Color
           (com.badlogic.gdx.scenes.scene2d.ui Button ButtonGroup)
           (com.badlogic.gdx.utils ScreenUtils SharedLibraryLoader)))

; contains at the bottom
;; options-menu, main-menu, screens/world, & oben minimap
; so app ==> screens (property editor not here)
; just pass to options-menu,e tc. stuff ?

; actually do I need those ?!

; * minimap, options-menu  -> integrate
; * maybe property/(== map-editor link from world gen property)  also integrate just ?
; * or separate app

(def-type :properties/app
  {:schema [:app/lwjgl3
            :app/context]
   :overview {:title "Apps" ; - only 1 ? - no overview - ?
              :columns 10}})

; 28.4 viewportwidth
; 16 viewportheight
; camera shows :
;  [-viewportWidth/2, -(viewportHeight/2-1)] - [(viewportWidth/2-1), viewportHeight/2]
; zoom default '1'
; zoom 2 -> shows double amount

; we want min/max explored tiles X / Y and show the whole explored area....

(defn- minimap-zoom [{:keys [context/explored-tile-corners] :as ctx}]
  (let [positions-explored (map first
                                (remove (fn [[position value]]
                                          (false? value))
                                        (seq @explored-tile-corners)))
        left   (apply min-key (fn [[x y]] x) positions-explored)
        top    (apply max-key (fn [[x y]] y) positions-explored)
        right  (apply max-key (fn [[x y]] x) positions-explored)
        bottom (apply min-key (fn [[x y]] y) positions-explored)]
    (calculate-zoom (world-camera ctx)
                    :left left
                    :top top
                    :right right
                    :bottom bottom)))

(defn- ->tile-corner-color-setter [explored?]
  (fn tile-corner-color-setter [color x y]
    (if (get explored? [x y])
      Color/WHITE
      Color/BLACK)))

#_(deftype Screen []
    (show [_ ctx]
      (set-zoom! (world-camera ctx) (minimap-zoom ctx)))

    (hide [_ ctx]
      (reset-zoom! (world-camera ctx)))

    ; TODO fixme not subscreen
    (render [_ {:keys [context/tiled-map context/explored-tile-corners] :as context}]
      (tiled/render! context
                     tiled-map
                     (->tile-corner-color-setter @explored-tile-corners))
      (render-world-view context
                         (fn [g]
                           (draw-filled-circle g
                                               (camera-position (world-camera context))
                                               0.5
                                               Color/GREEN)))
      (if (or (.isKeyJustPressed gdx-input Input$Keys/TAB)
              (.isKeyJustPressed gdx-input Input$Keys/ESCAPE))
        (change-screen context :screens/world)
        context)))

#_(defcomponent :screens/minimap
  (->mk [_ _ctx]
    (->Screen)))

(defn- geom-test [g ctx]
  (let [position (world-mouse-position ctx)
        grid (:context/grid ctx)
        radius 0.8
        circle {:position position :radius radius}]
    (draw-circle g position radius [1 0 0 0.5])
    (doseq [[x y] (map #(:position @%)
                       (circle->cells grid circle))]
      (draw-rectangle g x y 1 1 [1 0 0 0.5]))
    (let [{[x y] :left-bottom :keys [width height]} (circle->outer-rectangle circle)]
      (draw-rectangle g x y width height [0 0 1 1]))))

(def ^:private ^:dbg-flag tile-grid? false)
(def ^:private ^:dbg-flag potential-field-colors? false)
(def ^:private ^:dbg-flag cell-entities? false)
(def ^:private ^:dbg-flag cell-occupied? false)

(defn- tile-debug [g ctx]
  (let [grid (:context/grid ctx)
        world-camera (world-camera ctx)
        [left-x right-x bottom-y top-y] (frustum world-camera)]

    (when tile-grid?
      (draw-grid g (int left-x) (int bottom-y)
                 (inc (int (world-viewport-width ctx)))
                 (+ 2 (int (world-viewport-height ctx)))
                 1 1 [1 1 1 0.8]))

    (doseq [[x y] (visible-tiles world-camera)
            :let [cell (grid [x y])]
            :when cell
            :let [cell* @cell]]

      (when (and cell-entities? (seq (:entities cell*)))
        (draw-filled-rectangle g x y 1 1 [1 0 0 0.6]))

      (when (and cell-occupied? (seq (:occupied cell*)))
        (draw-filled-rectangle g x y 1 1 [0 0 1 0.6]))

      (when potential-field-colors?
        (let [faction :good
              {:keys [distance entity]} (faction cell*)]
          (when distance
            (let [ratio (/ distance (world/factions-iterations faction))]
              (draw-filled-rectangle g x y 1 1 [ratio (- 1 ratio) ratio 0.6]))))))))

(def ^:private ^:dbg-flag highlight-blocked-cell? true)

(defn- highlight-mouseover-tile [g ctx]
  (when highlight-blocked-cell?
    (let [[x y] (->tile (world-mouse-position ctx))
          cell (get (:context/grid ctx) [x y])]
      (when (and cell (#{:air :none} (:movement @cell)))
        (draw-rectangle g x y 1 1
                        (case (:movement @cell)
                          :air  [1 1 0 0.5]
                          :none [1 0 0 0.5]))))))

(defn- before-entities [ctx g]
  (tile-debug g ctx))

(defn- after-entities [ctx g]
  #_(geom-test g ctx)
  (highlight-mouseover-tile g ctx))

(defn- init-game-context [ctx & {:keys [mode record-transactions? tiled-level]}]
  (let [ctx (dissoc ctx :context/entity-tick-error)
        ctx (-> ctx
                (merge {:context/game-loop-mode mode}
                       (create-into ctx
                                    {:context/ecs true
                                     :context/time true
                                     :context/widgets true
                                     :context/effect-handler [mode record-transactions?]})))]
    (case mode
      :game-loop/normal (do
                         (when-let [tiled-map (:context/tiled-map ctx)]
                           (dispose tiled-map))
                         (-> ctx
                             (merge (world/->world-map tiled-level))
                             (world/spawn-creatures! tiled-level)))
      :game-loop/replay (merge ctx (world/->world-map (select-keys ctx [:context/tiled-map
                                                                        :context/start-position]))))))

(defn- start-new-game [ctx tiled-level]
  (init-game-context ctx
                     :mode :game-loop/normal
                     :record-transactions? false ; TODO top level flag ?
                     :tiled-level tiled-level))

(def ^:private ^:dbg-flag pausing? true) ; allow setting @ app/game config?
; context/world ... ?

(defn- player-unpaused? []
  (or (.isKeyJustPressed gdx-input Input$Keys/P)
      (.isKeyPressed     gdx-input Input$Keys/SPACE)))

(defn- update-game-paused [ctx]
  (assoc ctx :context/paused? (or (:context/entity-tick-error ctx)
                                  (and pausing?
                                       (player-state-pause-game? ctx)
                                       (not (player-unpaused?))))))

(defn- update-time [ctx delta]
  (update ctx ctx-time #(-> %
                            (assoc :delta-time delta)
                            (update :elapsed + delta)
                            (update :logic-frame inc))))

(defn- update-world [ctx]
  (let [ctx (update-time ctx (min (.getDeltaTime gdx-graphics) max-delta-time))
        entities (active-entities ctx)]
    (world/potential-fields-update! ctx entities)
    (try (tick-entities! ctx entities)
         (catch Throwable t
           (-> ctx
               (error-window! t)
               (assoc :context/entity-tick-error t))))))

(defmulti ^:private game-loop :context/game-loop-mode)

(defmethod game-loop :game-loop/normal [ctx]
  (effect! ctx [player-update-state
                update-mouseover-entity ; this do always so can get debug info even when game not running
                update-game-paused
                #(if (:context/paused? %)
                   %
                   (update-world %))
                remove-destroyed-entities! ; do not pause this as for example pickup item, should be destroyed.
                ]))

(defn- replay-frame! [ctx]
  (let [frame-number (logic-frame ctx)
        txs [:foo]#_(ctx/frame->txs ctx frame-number)]
    ;(println frame-number ". " (count txs))
    (-> ctx
        (effect! txs)
        #_(update :world.time/logic-frame inc))))  ; this is probably broken now (also frame->txs contains already time, so no need to inc ?)

(def ^:private replay-speed 2)

(defmethod game-loop :game-loop/replay [ctx]
  (reduce (fn [ctx _] (replay-frame! ctx))
          ctx
          (range replay-speed)))

(defn- render-world! [ctx]
  (camera-set-position! (world-camera ctx) (:position (player-entity* ctx)))
  (world/render-map ctx (camera-position (world-camera ctx)))
  (render-world-view ctx
                     (fn [g]
                       (before-entities ctx g)
                       (render-entities! ctx g (map deref (active-entities ctx)))
                       (after-entities ctx g))))

(defn- render-infostr-on-bar [g infostr x y h]
  (draw-text g {:text infostr
                :x (+ x 75)
                :y (+ y 2)
                :up? true}))

(defn- ->hp-mana-bars [context]
  (let [rahmen      (->image context "images/rahmen.png")
        hpcontent   (->image context "images/hp.png")
        manacontent (->image context "images/mana.png")
        x (/ (gui-viewport-width context) 2)
        [rahmenw rahmenh] (:pixel-dimensions rahmen)
        y-mana 80 ; action-bar-icon-size
        y-hp (+ y-mana rahmenh)
        render-hpmana-bar (fn [g ctx x y contentimg minmaxval name]
                            (draw-image g rahmen [x y])
                            (draw-image g
                                        (sub-image ctx contentimg [0 0 (* rahmenw (val-max-ratio minmaxval)) rahmenh])
                                        [x y])
                            (render-infostr-on-bar g (str (readable-number (minmaxval 0)) "/" (minmaxval 1) " " name) x y rahmenh))]
    (->actor {:draw (fn [g ctx]
                         (let [player-entity* (player-entity* ctx)
                               x (- x (/ rahmenw 2))]
                           (render-hpmana-bar g ctx x y-hp   hpcontent   (entity-stat player-entity* :stats/hp) "HP")
                           (render-hpmana-bar g ctx x y-mana manacontent (entity-stat player-entity* :stats/mana) "MP")))})))

(defn- skill-info [{:keys [entity/skills]}]
  (clojure.string/join "\n"
                       (for [{:keys [property/id skill/cooling-down?]} (vals skills)
                             :when cooling-down? ]
                         [id [:cooling-down? (boolean cooling-down?)]])))

; TODO component to info-text move to the component itself.....
(defn- debug-infos ^String [ctx]
  (let [world-mouse (world-mouse-position ctx)]
    (str
     "logic-frame: " (logic-frame ctx) "\n"
     "FPS: " (.getFramesPerSecond gdx-graphics)  "\n"
     "Zoom: " (zoom (world-camera ctx)) "\n"
     "World: "(mapv int world-mouse) "\n"
     "X:" (world-mouse 0) "\n"
     "Y:" (world-mouse 1) "\n"
     "GUI: " (gui-mouse-position ctx) "\n"
     "paused? " (:context/paused? ctx) "\n"
     "elapsed-time " (readable-number (elapsed-time ctx)) " seconds \n"
     (skill-info (player-entity* ctx))
     (when-let [entity* (mouseover-entity* ctx)]
       (str "Mouseover-entity uid: " (:entity/uid entity*)))
     ;"\nMouseover-Actor:\n"
     #_(when-let [actor (mouse-on-actor? ctx)]
         (str "TRUE - name:" (.getName actor)
              "id: " (actor-id actor)
              )))))

(defn- ->debug-window [context]
  (let [label (->label "")
        window (->window {:title "Debug"
                          :id :debug-window
                          :visible? false
                          :position [0 (gui-viewport-height context)]
                          :rows [[label]]})]
    (add-actor! window (->actor {:act #(do
                                        (.setText label (debug-infos %))
                                        (.pack window))}))
    window))

(def ^:private disallowed-keys [:entity/skills
                                :entity/state
                                :entity/faction
                                :active-skill])

(defn- ->entity-info-window [context]
  (let [label (->label "")
        window (->window {:title "Info"
                          :id :entity-info-window
                          :visible? false
                          :position [(gui-viewport-width context) 0]
                          :rows [[{:actor label :expand? true}]]})]
    ; TODO do not change window size ... -> no need to invalidate layout, set the whole stage up again
    ; => fix size somehow.
    (add-actor! window (->actor {:act (fn update-label-text [ctx]
                                        ; items then have 2x pretty-name
                                        #_(.setText (.getTitleLabel window)
                                                    (if-let [entity* (mouseover-entity* ctx)]
                                                      (info-text [:property/pretty-name (:property/pretty-name entity*)])
                                                      "Entity Info"))
                                        (.setText label
                                                  (str (when-let [entity* (mouseover-entity* ctx)]
                                                         (->info-text
                                                          ; don't use select-keys as it loses Entity record type
                                                          (apply dissoc entity* disallowed-keys)
                                                          ctx))))
                                        (.pack window))}))
    window))

(def ^:private image-scale 2)

(defn- ->action-bar []
  (let [group (->horizontal-group {:pad 2 :space 2})]
    (set-id! group ::action-bar)
    group))

(defn- ->action-bar-button-group []
  (->button-group {:max-check-count 1
                      :min-check-count 0}))

(defn- get-action-bar [ctx]
  {:horizontal-group (::action-bar (:action-bar-table (stage-get ctx)))
   :button-group (:action-bar (:context/widgets ctx))})

(defcomponent :tx.action-bar/add
  (do! [[_ {:keys [property/id entity/image] :as skill}] ctx]
    (let [{:keys [horizontal-group button-group]} (get-action-bar ctx)
          button (->image-button image identity {:scale image-scale})]
      (set-id! button id)
      (add-tooltip! button
                          #(->info-text skill (assoc % :effect/source (player-entity %))))
      (add-actor! horizontal-group button)
      (.add ^ButtonGroup button-group ^Button button)
      ctx)))

(defcomponent :tx.action-bar/remove
  (do! [[_ {:keys [property/id]}] ctx]
    (let [{:keys [horizontal-group button-group]} (get-action-bar ctx)
          button (get horizontal-group id)]
      (remove! button)
      (.remove ^ButtonGroup button-group ^Button button)
      ctx)))

(comment

 (comment
  (def sword-button (.getChecked button-group))
  (.setChecked sword-button false)
  )

 #_(defn- number-str->input-key [number-str]
     (eval (symbol (str "com.badlogic.gdx.Input$Keys/NUM_" number-str))))

 ; TODO do with an actor
 ; .getChildren horizontal-group => in order
 (defn up-skill-hotkeys []
   #_(doseq [slot slot-keys
             :let [skill-id (slot @slot->skill-id)]
             :when (and (.isKeyJustPressed gdx-input (number-str->input-key (name slot)))
                        skill-id)]
       (.setChecked ^Button (.findActor horizontal-group (str skill-id)) true)))

 ; TODO
 ; * cooldown / not usable -> diff. colors ? disable on not able to use skills (stunned?)
 ; * or even sector circling for cooldown like in WoW (clipped !)
 ; * tooltips ! with hotkey-number !
 ;  ( (skills/text skill-id player-entity))
 ; * add hotkey number to tooltips
 ; * hotkeys => select button
 ; when no selected-skill & new skill assoce'd (sword at start)
 ; => set selected
 ; keep weapon at position 1 always ?

 #_(def ^:private slot-keys {:1 input.keys.num-1
                             :2 input.keys.num-2
                             :3 input.keys.num-3
                             :4 input.keys.num-4
                             :5 input.keys.num-5
                             :6 input.keys.num-6
                             :7 input.keys.num-7
                             :8 input.keys.num-8
                             :9 input.keys.num-9})

 #_(defn- empty-slot->skill-id []
     (apply sorted-map
            (interleave slot-keys
                        (repeat nil))))

 #_(def selected-skill-id (atom nil))
 #_(def ^:private slot->skill-id (atom nil))

 #_(defn reset-skills! []
     (reset! selected-skill-id nil)
     (reset! slot->skill-id (empty-slot->skill-id)))


 ; https://javadoc.io/doc/com.badlogicgames.gdx/gdx/latest/com/badlogic/gdx/scenes/scene2d/ui/Button.html
 (.setProgrammaticChangeEvents ^Button (.findActor horizontal-group ":skills/spawn") true)
 ; but doesn't toggle:
 (.toggle ^Button (.findActor horizontal-group ":skills/spawn"))
 (.setChecked ^Button (.findActor horizontal-group ":skills/spawn") true)
 ; Toggles the checked state. This method changes the checked state, which fires a ChangeListener.ChangeEvent (if programmatic change events are enabled), so can be used to simulate a button click.

 ; => it _worked_ => active skill changed
 ; only button is not highlighted idk why

 (.getChildren horizontal-group)

 )

;; world widgets/ui ?
; what context?!

(defn- ->ui-actors [ctx widget-data]
  [(->table {:rows [[{:actor (->action-bar)
                      :expand? true
                      :bottom? true}]]
             :id :action-bar-table
             :cell-defaults {:pad 2}
             :fill-parent? true})
   (->hp-mana-bars ctx)
   (->group {:id :windows
             :actors [(->debug-window ctx)
                      (->entity-info-window ctx)
                      (inventory/->build ctx widget-data)]})
   (->actor {:draw draw-item-on-cursor})
   (->mk [:widgets/player-message] ctx)])


(defcomponent :context/widgets
  (->mk [_ ctx]
    (let [widget-data {:action-bar (->action-bar-button-group)
                       :slot->background (inventory/->data ctx)}
          stage (stage-get ctx)]
      (.clear stage)
      (run! #(.addActor stage %) (->ui-actors ctx widget-data))
      widget-data)))

(defn- hotkey->window-id [{:keys [context/config]}]
  (merge {Input$Keys/I :inventory-window
          Input$Keys/E :entity-info-window}
         (when (safe-get config :debug-window?)
           {Input$Keys/Z :debug-window})))

(defn- check-window-hotkeys [ctx]
  (doseq [[hotkey window-id] (hotkey->window-id ctx)
          :when (.isKeyJustPressed gdx-input hotkey)]
    (toggle-visible! (get (:windows (stage-get ctx)) window-id))))

(defn- close-windows?! [context]
  (let [windows (children (:windows (stage-get context)))]
    (if (some visible? windows)
      (do
       (run! #(set-visible! % false) windows)
       true))))

(defn- adjust-zoom [camera by] ; DRY map editor
  (set-zoom! camera (max 0.1 (+ (zoom camera) by))))

(def ^:private zoom-speed 0.05)

(defn- check-zoom-keys [ctx]
  (let [camera (world-camera ctx)]
    (when (.isKeyPressed gdx-input Input$Keys/MINUS)  (adjust-zoom camera    zoom-speed))
    (when (.isKeyPressed gdx-input Input$Keys/EQUALS) (adjust-zoom camera (- zoom-speed)))))

; TODO move to actor/stage listeners ? then input processor used ....
(defn- check-key-input [ctx]
  (check-zoom-keys ctx)
  (check-window-hotkeys ctx)
  (cond (and (.isKeyJustPressed gdx-input Input$Keys/ESCAPE)
             (not (close-windows?! ctx)))
        (change-screen ctx :screens/options-menu)

        ; TODO not implementing StageSubScreen so NPE no screen-render!
        #_(.isKeyJustPressed gdx-input Input$Keys/TAB)
        #_(change-screen ctx :screens/minimap)

        :else
        ctx))

(defcomponent :world/sub-screen
  (screen-exit [_ ctx]
    (set-cursor! ctx :cursors/default))

  (screen-render [_ ctx]
    (render-world! ctx)
    (-> ctx
        game-loop
        check-key-input)))

(derive :screens/world :screens/stage)
(defcomponent :screens/world
  (->mk [_ ctx]
    {:stage (->stage ctx [])
     :sub-screen [:world/sub-screen]}))

(comment

 ; https://github.com/damn/core/issues/32
 ; grep :game-loop/replay
 ; unused code & not working
 ; record only top-lvl txs or save world state every x minutes/seconds

 ; TODO @replay-mode
 ; * do I need check-key-input from screens/world?
 ; adjust sound speed also equally ? pitch ?
 ; player message, player modals, etc. all game related state handle ....
 ; game timer is not reset  - continues as if
 ; check other atoms , try to remove atoms ...... !?
 ; replay mode no window hotkeys working
 ; buttons working
 ; can remove items from inventory ! changes cursor but does not change back ..
 ; => deactivate all input somehow (set input processor nil ?)
 ; works but ESC is separate from main input processor and on re-entry
 ; again stage is input-processor
 ; also cursor is from previous game replay
 ; => all hotkeys etc part of stage input processor make.
 ; set nil for non idle/item in hand states .
 ; for some reason he calls end of frame checks but cannot open windows with hotkeys

 (defn- start-replay-mode! [ctx]
   (.setInputProcessor gdx-input nil)
   (init-game-context ctx :mode :game-loop/replay))

 (.postRunnable gdx-app
  (fn []
    (swap! app-state start-replay-mode!)))

 )

(defn- start-game! [world-id]
  (fn [ctx]
    (-> ctx
        (change-screen :screens/world)
        (start-new-game (->world ctx world-id)))))

;; we are in 'world' namespace?
; then where does main-menu/minimap/etc go
; actually do I need them ?!

(defn- ->buttons [{:keys [context/config] :as ctx}]
  (->table {:rows (remove nil? (concat
                                   (for [{:keys [property/id]} (all-properties ctx :properties/worlds)]
                                     [(->text-button (str "Start " id) (start-game! id))])
                                   [(when (safe-get config :map-editor?)
                                      [(->text-button "Map editor" #(change-screen % :screens/map-editor))])
                                    (when (safe-get config :property-editor?)
                                      [(->text-button "Property editor" #(change-screen % :screens/property-editor))])
                                    [(->text-button "Exit" (fn [ctx] (.exit gdx-app) ctx))]]))
               :cell-defaults {:pad-bottom 25}
               :fill-parent? true}))


(defcomponent :main/sub-screen
  (screen-enter [_ ctx]
    (set-cursor! ctx :cursors/default)))

(defn- ->actors [ctx]
  [(->background-image ctx)
   (->buttons ctx)
   (->actor {:act (fn [_ctx]
                       (when (.isKeyJustPressed gdx-input Input$Keys/ESCAPE)
                         (.exit gdx-app)))})])

(derive :screens/main-menu :screens/stage)
(defcomponent :screens/main-menu
  (->mk [[k _] ctx]
    {:sub-screen [:main/sub-screen]
     :stage (->stage ctx (->actors ctx))}))

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
    (.bindRoot avar is-selected)))

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

(defn- create-table [{:keys [context/config] :as ctx}]
  (->table {:rows (concat
                      [[(->label key-help-text)]]

                      (when (safe-get config :debug-window?)
                        [[(->label "[Z] - Debug window")]])

                      (when (safe-get config :debug-options?)
                        (for [check-box debug-flags]
                          [(->check-box (get-text check-box)
                                           (partial set-state check-box)
                                           (boolean (get-state check-box)))]))

                      [[(->text-button "Resume" #(change-screen % :screens/world))]

                       [(->text-button "Exit" #(change-screen % :screens/main-menu))]])

               :fill-parent? true
               :cell-defaults {:pad-bottom 10}}))

(defcomponent :options/sub-screen
  (screen-render [_ ctx]
    (if (.isKeyJustPressed gdx-input Input$Keys/ESCAPE)
      (change-screen ctx :screens/world)
      ctx)))

(derive :screens/options-menu :screens/stage)
(defcomponent :screens/options-menu
  (->mk [_ ctx]
    {:stage (->stage ctx [(->background-image ctx) (create-table ctx)])
     :sub-screen [:options/sub-screen]}))

(defn- set-first-screen [context]
  (->> context
       :context/screens
       :first-screen
       (change-screen context)))

(defn- render! [app-state]
  (screen-render! (current-screen @app-state) app-state))

(defn- ->application-listener [context]
  (proxy [ApplicationAdapter] []
    (create []
      (bind-gdx-statics!)
      (->> context
           ; screens require vis-ui / properties (map-editor, property editor uses properties)
           (sort-by (fn [[k _]] (if (= k :context/screens) 1 0)))
           (create-into context)
           set-first-screen
           (reset! app-state)))

    (dispose []
      (run! destroy! @app-state))

    (render []
      (ScreenUtils/clear Color/BLACK)
      (render! app-state))

    (resize [w h]
      ; TODO fix mac screen resize bug again
      (on-resize @app-state w h))))

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

(defn- ->lwjgl3-app [listener config]
  (Lwjgl3Application. listener config))

(defn -main []

  ; TODO this is part of lwjgl3 config - mac can pass config key

  ; https://github.com/libgdx/libgdx/pull/7361
  ; Maybe can delete this when using that new libgdx version
  ; which includes this PR.
  (when SharedLibraryLoader/isMac
    (.set Configuration/GLFW_LIBRARY_NAME "glfw_async")
    (.set Configuration/GLFW_CHECK_THREAD0 false))



  (let [ctx (map->Ctx {:context/properties
                       (validate-and-create "resources/properties.edn")})
        app (build-property ctx :app/core)]
    ; the app internally reads the config & appl - listener
    ; and lwjgl3/keys for config
    ; and app/lwjg3-config
    (->lwjgl3-app (->application-listener (safe-merge ctx (:app/context app)))
                  (->lwjgl3-app-config (:app/lwjgl3 app)))))
