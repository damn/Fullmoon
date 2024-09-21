(ns core.screens.world
  (:require [utils.core :refer [tile->middle]]
            [gdx.graphics.camera :as camera]
            [gdx.maps.tiled :as tiled]
            [core.component :refer [defcomponent] :as component]
            [core.context :as ctx]
            [core.screen :as screen]
            [core.state :as state]
            [core.tx :as tx]
            [core.world.grid :as world-grid]
            [core.world.content-grid :as content-grid]
            [core.world.cell :as cell]
            [core.context.render :as world-render]
           [core.context.debug-render :as debug-render])
  (:import (com.badlogic.gdx Gdx Input$Keys)
           com.badlogic.gdx.utils.Disposable))

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
  (ctx/do! ctx
           (for [creature (cons (world->player-creature ctx tiled-level)
                                (when spawn-enemies?
                                  (world->enemy-creatures ctx)))]
             [:tx/creature (update creature :position tile->middle)])))

; TODO https://github.com/damn/core/issues/57
; (check-not-allowed-diagonals grid)
; done at module-gen? but not custom tiledmap?
(defn- ->world-map [{:keys [tiled-map start-position]}]
  (component/create-into {:context/tiled-map tiled-map
                          :context/start-position start-position}
                         {:context/grid [(tiled/width  tiled-map)
                                         (tiled/height tiled-map)
                                       #(case (tiled/movement-property tiled-map %)
                                          "none" :none
                                          "air"  :air
                                          "all"  :all)]
                          :context/raycaster cell/blocks-vision?
                          :context/content-grid [16 16]
                          :context/explored-tile-corners true}))

(defn- init-game-context [ctx & {:keys [mode record-transactions? tiled-level]}]
  (let [ctx (dissoc ctx :context/entity-tick-error)
        ctx (-> ctx
                (merge {:context/game-loop-mode mode}
                       (component/create-into ctx
                                              {:context/ecs true
                                               :context/time true
                                               :context/widgets true
                                               :context/effect-handler [mode record-transactions?]})))]
    (case mode
      :game-loop/normal (do
                         (when-let [tiled-map (:context/tiled-map ctx)]
                           (.dispose ^Disposable tiled-map))
                         (-> ctx
                             (merge (->world-map tiled-level))
                             (spawn-creatures! tiled-level)))
      :game-loop/replay (merge ctx (->world-map (select-keys ctx [:context/tiled-map
                                                                  :context/start-position]))))))

(extend-type core.context.Context
  core.context/World
  (start-new-game [ctx tiled-level]
    (init-game-context ctx
                       :mode :game-loop/normal
                       :record-transactions? false ; TODO top level flag ?
                       :tiled-level tiled-level))

  ; TODO these two what to do ?
  (content-grid [ctx] (:context/content-grid ctx))

  (active-entities [ctx]
    (content-grid/active-entities (ctx/content-grid ctx)
                                  (ctx/player-entity* ctx)))

  (world-grid [ctx] (:context/grid ctx)))

(defcomponent :tx/add-to-world
  (tx/do! [[_ entity] ctx]
    (content-grid/update-entity! (ctx/content-grid ctx) entity)
    ; https://github.com/damn/core/issues/58
    ;(assert (valid-position? grid @entity)) ; TODO deactivate because projectile no left-bottom remove that field or update properly for all
    (world-grid/add-entity! (ctx/world-grid ctx) entity)
    ctx))

(defcomponent :tx/remove-from-world
  (tx/do! [[_ entity] ctx]
    (content-grid/remove-entity! (ctx/content-grid ctx) entity)
    (world-grid/remove-entity! (ctx/world-grid ctx) entity)
    ctx))

(defcomponent :tx/position-changed
  (tx/do! [[_ entity] ctx]
    (content-grid/update-entity! (ctx/content-grid ctx) entity)
    (world-grid/entity-position-changed! (ctx/world-grid ctx) entity)
    ctx))

(def ^:private ^:dbg-flag pausing? true)

(defn- player-unpaused? []
  (or (.isKeyJustPressed Gdx/input Input$Keys/P)
      (.isKeyPressed     Gdx/input Input$Keys/SPACE)))

(defn- update-game-paused [ctx]
  (assoc ctx :context/paused? (or (:context/entity-tick-error ctx)
                                  (and pausing?
                                       (ctx/player-state-pause-game? ctx)
                                       (not (player-unpaused?))))))

(defn- update-world [ctx]
  (let [ctx (ctx/update-time ctx)
        active-entities (ctx/active-entities ctx)]
    (ctx/update-potential-fields ctx active-entities)
    (try (ctx/tick-entities! ctx active-entities)
         (catch Throwable t
           (-> ctx
               (ctx/error-window! t)
               (assoc :context/entity-tick-error t))))))

(defmulti ^:private game-loop :context/game-loop-mode)

(defmethod game-loop :game-loop/normal [ctx]
  (ctx/do! ctx [ctx/player-update-state
                ctx/update-mouseover-entity ; this do always so can get debug info even when game not running
                update-game-paused
                #(if (:context/paused? %)
                   %
                   (update-world %))
                ctx/remove-destroyed-entities! ; do not pause this as for example pickup item, should be destroyed.
                ]))

(defn- replay-frame! [ctx]
  (let [frame-number (ctx/logic-frame ctx)
        txs (ctx/frame->txs ctx frame-number)]
    ;(println frame-number ". " (count txs))
    (-> ctx
        (ctx/do! txs)
        #_(update :world.time/logic-frame inc))))  ; this is probably broken now (also frame->txs contains already time, so no need to inc ?)

(def ^:private replay-speed 2)

(defmethod game-loop :game-loop/replay [ctx]
  (reduce (fn [ctx _] (replay-frame! ctx))
          ctx
          (range replay-speed)))

(defn- render-world! [ctx]
  (camera/set-position! (ctx/world-camera ctx) (:position (ctx/player-entity* ctx)))
  (world-render/render-map ctx (camera/position (ctx/world-camera ctx)))
  (ctx/render-world-view ctx
                         (fn [g]
                           (debug-render/before-entities ctx g)
                           (ctx/render-entities! ctx
                                                 g
                                                 (->> (ctx/active-entities ctx)
                                                      (map deref)))
                           (debug-render/after-entities ctx g))))

(defn- adjust-zoom [camera by] ; DRY map editor
  (camera/set-zoom! camera (max 0.1 (+ (camera/zoom camera) by))))

(def ^:private zoom-speed 0.05)

(defn- check-zoom-keys [ctx]
  (let [camera (ctx/world-camera ctx)]
    (when (.isKeyPressed Gdx/input Input$Keys/MINUS)  (adjust-zoom camera    zoom-speed))
    (when (.isKeyPressed Gdx/input Input$Keys/EQUALS) (adjust-zoom camera (- zoom-speed)))))

; TODO move to actor/stage listeners ? then input processor used ....
(defn- check-key-input [ctx]
  (check-zoom-keys ctx)
  (ctx/check-window-hotkeys ctx)
  (cond (and (.isKeyJustPressed Gdx/input Input$Keys/ESCAPE)
             (not (ctx/close-windows? ctx)))
        (ctx/change-screen ctx :screens/options-menu)

        ; TODO not implementing StageSubScreen so NPE no screen/render!
        #_(.isKeyJustPressed Gdx/input Input$Keys/TAB)
        #_(ctx/change-screen ctx :screens/minimap)

        :else
        ctx))

(defcomponent ::sub-screen
  (state/exit [_ ctx]
    (ctx/set-cursor! ctx :cursors/default))

  (screen/render [_ ctx]
    (render-world! ctx)
    (-> ctx
        game-loop
        check-key-input)))

(derive :screens/world :screens/stage-screen)
(defcomponent :screens/world
  (component/create [_ ctx]
    {:stage (ctx/->stage ctx [])
     :sub-screen [::sub-screen]}))

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
   (.setInputProcessor Gdx/input nil)
   (init-game-context ctx :mode :game-loop/replay))

 (.postRunnable com.badlogic.gdx.Gdx/app
  (fn []
    (swap! core.app/state start-replay-mode!)))

 )
