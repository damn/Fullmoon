(ns core.world
  (:require [core.utils.core :refer [tile->middle safe-get]]
            [core.ctx :refer :all]
            [core.tiled :as tiled]
            [core.screen :as screen]
            [core.screens :as screens]
            [core.stage :as stage]
            [core.property :as property]
            [core.graphics.cursors :as cursors]
            [core.graphics.camera :as camera]
            [core.ui :as ui]
            [core.entity :as entity]
            [core.entity.player :as player]
            [core.property.types.world :as level-generator]
            [core.widgets.error-modal :refer [error-window!]]
            [core.widgets.background-image :refer [->background-image]]
            [core.world.ecs :as ecs]
            [core.world.widgets :as widgets]
            [core.world.content-grid :as content-grid]
            [core.world.grid :as grid]
            [core.world.mouseover-entity :refer [update-mouseover-entity]]
            [core.world.time :as time]
            [core.world.potential-fields :as potential-fields]
            [core.world.render.tiled-map :as world-render]
            [core.world.render.debug :as debug-render]  )
  (:import com.badlogic.gdx.Input$Keys))

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
(defn- world->player-creature [{:keys [context/start-position]}
                               {:keys [world/player-creature]}]
  {:position start-position
   :creature-id :creatures/vampire #_(:property/id player-creature)
   :components player-components})

(defn- world->enemy-creatures [{:keys [context/tiled-map]}]
  (for [[position creature-id] (tiled/positions-with-property tiled-map :creatures :id)]
    {:position position
     :creature-id (keyword creature-id)
     :components npc-components}))

(defn- spawn-creatures! [ctx tiled-level]
  (effect! ctx
           (for [creature (cons (world->player-creature ctx tiled-level)
                                (when spawn-enemies?
                                  (world->enemy-creatures ctx)))]
             [:tx/creature (update creature :position tile->middle)])))

; TODO https://github.com/damn/core/issues/57
; (check-not-allowed-diagonals grid)
; done at module-gen? but not custom tiledmap?
(defn- ->world-map [{:keys [tiled-map start-position]}] ; == one object make ?! like graphics?
  ; grep context/grid -> all dependent stuff?
  (create-into {:context/tiled-map tiled-map
                :context/start-position start-position}
               {:context/grid [(tiled/width  tiled-map)
                               (tiled/height tiled-map)
                               #(case (tiled/movement-property tiled-map %)
                                  "none" :none
                                  "air"  :air
                                  "all"  :all)]
                :context/raycaster grid/blocks-vision?
                :context/content-grid [16 16]
                :context/explored-tile-corners true}))

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
                             (merge (->world-map tiled-level))
                             (spawn-creatures! tiled-level)))
      :game-loop/replay (merge ctx (->world-map (select-keys ctx [:context/tiled-map
                                                                  :context/start-position]))))))

(defn ^:no-doc start-new-game [ctx tiled-level]
  (init-game-context ctx
                     :mode :game-loop/normal
                     :record-transactions? false ; TODO top level flag ?
                     :tiled-level tiled-level))

(defn active-entities [ctx]
  (content-grid/active-entities ctx (player-entity* ctx)))

(defcomponent :tx/add-to-world
  (do! [[_ entity] ctx]
    (content-grid/update-entity! ctx entity)
    ; https://github.com/damn/core/issues/58
    ;(assert (valid-position? grid @entity)) ; TODO deactivate because projectile no left-bottom remove that field or update properly for all
    (grid/add-entity! ctx entity)
    ctx))

(defcomponent :tx/remove-from-world
  (do! [[_ entity] ctx]
    (content-grid/remove-entity! ctx entity)
    (grid/remove-entity! ctx entity)
    ctx))

(defcomponent :tx/position-changed
  (do! [[_ entity] ctx]
    (content-grid/update-entity! ctx entity)
    (grid/entity-position-changed! ctx entity)
    ctx))

(def ^:private ^:dbg-flag pausing? true)

(defn- player-unpaused? []
  (or (.isKeyJustPressed gdx-input Input$Keys/P)
      (.isKeyPressed     gdx-input Input$Keys/SPACE)))

(defn- update-game-paused [ctx]
  (assoc ctx :context/paused? (or (:context/entity-tick-error ctx)
                                  (and pausing?
                                       (player/state-pause-game? ctx)
                                       (not (player-unpaused?))))))

(defn- update-world [ctx]
  (let [ctx (time/update-time ctx (min (.getDeltaTime gdx-graphics) entity/max-delta-time))
        active-entities (active-entities ctx)]
    (potential-fields/update! ctx active-entities)
    (try (ecs/tick-entities! ctx active-entities)
         (catch Throwable t
           (-> ctx
               (error-window! t)
               (assoc :context/entity-tick-error t))))))

(defmulti ^:private game-loop :context/game-loop-mode)

(defmethod game-loop :game-loop/normal [ctx]
  (effect! ctx [player/update-state
                update-mouseover-entity ; this do always so can get debug info even when game not running
                update-game-paused
                #(if (:context/paused? %)
                   %
                   (update-world %))
                ecs/remove-destroyed-entities! ; do not pause this as for example pickup item, should be destroyed.
                ]))

(defn- replay-frame! [ctx]
  (let [frame-number (time/logic-frame ctx)
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
  (camera/set-position! (world-camera ctx) (:position (player-entity* ctx)))
  (world-render/render-map ctx (camera/position (world-camera ctx)))
  (render-world-view ctx
                     (fn [g]
                       (debug-render/before-entities ctx g)
                       (ecs/render-entities! ctx
                                             g
                                             (->> (active-entities ctx)
                                                  (map deref)))
                       (debug-render/after-entities ctx g))))

(defn- adjust-zoom [camera by] ; DRY map editor
  (camera/set-zoom! camera (max 0.1 (+ (camera/zoom camera) by))))

(def ^:private zoom-speed 0.05)

(defn- check-zoom-keys [ctx]
  (let [camera (world-camera ctx)]
    (when (.isKeyPressed gdx-input Input$Keys/MINUS)  (adjust-zoom camera    zoom-speed))
    (when (.isKeyPressed gdx-input Input$Keys/EQUALS) (adjust-zoom camera (- zoom-speed)))))

; TODO move to actor/stage listeners ? then input processor used ....
(defn- check-key-input [ctx]
  (check-zoom-keys ctx)
  (widgets/check-window-hotkeys ctx)
  (cond (and (.isKeyJustPressed gdx-input Input$Keys/ESCAPE)
             (not (widgets/close-windows? ctx)))
        (screens/change-screen ctx :screens/options-menu)

        ; TODO not implementing StageSubScreen so NPE no screen/render!
        #_(.isKeyJustPressed gdx-input Input$Keys/TAB)
        #_(screens/change-screen ctx :screens/minimap)

        :else
        ctx))

(defcomponent :world/sub-screen
  (screen/exit [_ ctx]
    (cursors/set-cursor! ctx :cursors/default))

  (screen/render [_ ctx]
    (render-world! ctx)
    (-> ctx
        game-loop
        check-key-input)))

(derive :screens/world :screens/stage)
(defcomponent :screens/world
  (->mk [_ ctx]
    {:stage (stage/create ctx [])
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
        (screens/change-screen :screens/world)
        (start-new-game (level-generator/->world ctx world-id)))))

(defn- ->buttons [{:keys [context/config] :as ctx}]
  (ui/->table {:rows (remove nil? (concat
                                   (for [{:keys [property/id]} (property/all-properties ctx :properties/worlds)]
                                     [(ui/->text-button (str "Start " id) (start-game! id))])
                                   [(when (safe-get config :map-editor?)
                                      [(ui/->text-button "Map editor" #(screens/change-screen % :screens/map-editor))])
                                    (when (safe-get config :property-editor?)
                                      [(ui/->text-button "Property editor" #(screens/change-screen % :screens/property-editor))])
                                    [(ui/->text-button "Exit" (fn [ctx] (.exit gdx-app) ctx))]]))
               :cell-defaults {:pad-bottom 25}
               :fill-parent? true}))


(defcomponent :main/sub-screen
  (screen/enter [_ ctx]
    (cursors/set-cursor! ctx :cursors/default)))

(defn- ->actors [ctx]
  [(->background-image ctx)
   (->buttons ctx)
   (ui/->actor {:act (fn [_ctx]
                       (when (.isKeyJustPressed gdx-input Input$Keys/ESCAPE)
                         (.exit gdx-app)))})])

(derive :screens/main-menu :screens/stage)
(defcomponent :screens/main-menu
  (->mk [[k _] ctx]
    {:sub-screen [:main/sub-screen]
     :stage (stage/create ctx (->actors ctx))}))
