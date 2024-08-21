(ns context.world
  (:require [clj-commons.pretty.repl :as p]
            [gdx.app :as app]
            [gdx.graphics.camera :as camera]
            [gdx.graphics.orthographic-camera :as orthographic-camera]
            [gdx.input :as input]
            [gdx.input.keys :as input.keys]
            [gdx.utils.disposable :refer [dispose]]
            [math.raycaster :as raycaster]
            [math.vector :as v]
            [utils.core :refer [tile->middle]]
            [core.component :refer [defcomponent]]
            [data.grid2d :as grid2d]
            [api.context :as ctx :refer [explored? ray-blocked? content-grid world-grid]]
            [api.entity :as entity]
            [api.effect :as effect]
            [api.maps.tiled :as tiled]
            [api.world.grid :as world-grid]
            [api.world.content-grid :as content-grid]
            [api.world.cell :as cell]
            (context.game [ecs :as ecs]
                          [mouseover-entity :as mouseover-entity]
                          [time :as time-component]
                          [effect-handler :as tx-handler]
                          [widgets :as widgets]
                          [debug-render :as debug-render])
            (context.game.world grid
                                content-grid
                                [potential-fields :as potential-fields]
                                render)
            [mapgen.movement-property :refer (movement-property)]))

(defn- on-screen? [entity* ctx]
  (let [[x y] (:position entity*)
        x (float x)
        y (float y)
        [cx cy] (camera/position (ctx/world-camera ctx))
        px (float cx)
        py (float cy)
        xdist (Math/abs (- x px))
        ydist (Math/abs (- y py))]
    (and
     (<= xdist (inc (/ (float (ctx/world-viewport-width ctx))  2)))
     (<= ydist (inc (/ (float (ctx/world-viewport-height ctx)) 2))))))

; TO math.... // not tested
(defn- create-double-ray-endpositions
  "path-w in tiles."
  [[start-x start-y] [target-x target-y] path-w]
  {:pre [(< path-w 0.98)]} ; wieso 0.98??
  (let [path-w (+ path-w 0.02) ;etwas gr�sser damit z.b. projektil nicht an ecken anst�sst
        v (v/direction [start-x start-y]
                       [target-y target-y])
        [normal1 normal2] (v/get-normal-vectors v)
        normal1 (v/scale normal1 (/ path-w 2))
        normal2 (v/scale normal2 (/ path-w 2))
        start1  (v/add [start-x  start-y]  normal1)
        start2  (v/add [start-x  start-y]  normal2)
        target1 (v/add [target-x target-y] normal1)
        target2 (v/add [target-x target-y] normal2)]
    [start1,target1,start2,target2]))

(def ^:private los-checks? true)

(extend-type api.context.Context
  api.context/World
  (update-potential-fields! [ctx entities]
    (potential-fields/update-potential-fields! (ctx/world-grid ctx)
                                               entities))

  (potential-field-follow-to-enemy [ctx entity]
    (potential-fields/potential-field-follow-to-enemy (ctx/world-grid ctx)
                                                      entity))

  (render-map [ctx]
    (context.game.world.render/render-map ctx (camera/position (ctx/world-camera ctx))))

  (line-of-sight? [context source* target*]
    (and (:z-order target*)  ; is even an entity which renders something
         (or (not (:entity/player? source*))
             (on-screen? target* context))
         (not (and los-checks?
                   (ray-blocked? context (:position source*) (:position target*))))))

  (ray-blocked? [{:keys [context.game/world]} start target]
    (let [{:keys [cell-blocked-boolean-array width height]} world]
      (raycaster/ray-blocked? cell-blocked-boolean-array width height start target)))

  (path-blocked? [context start target path-w]
    (let [[start1,target1,start2,target2] (create-double-ray-endpositions start target path-w)]
      (or
       (ray-blocked? context start1 target1)
       (ray-blocked? context start2 target2))))

  ; TODO put tile param
  (explored? [{:keys [context.game/world] :as context} position]
    (get @(:explored-tile-corners world) position))

  (content-grid [{:keys [context.game/world]}]
    (:content-grid world))

  (world-grid [{:keys [context.game/world]}]
    (:grid world)))

(defcomponent :tx/add-to-world {}
  (effect/do! [[_ entity] ctx]
    (content-grid/update-entity! (content-grid ctx) entity)
    ; hmm
    ;(assert (valid-position? grid @entity)) ; TODO deactivate because projectile no left-bottom remove that field or update properly for all
    (world-grid/add-entity! (world-grid ctx) entity)
    ctx))

(defcomponent :tx/remove-from-world {}
  (effect/do! [[_ entity] ctx]
    (content-grid/remove-entity! (content-grid ctx) entity)
    (world-grid/remove-entity! (world-grid ctx) entity)
    ctx))

(defcomponent :tx/position-changed {}
  (effect/do! [[_ entity] ctx]
    (content-grid/update-entity! (content-grid ctx) entity)
    (world-grid/entity-position-changed! (world-grid ctx) entity)
    ctx))

(defn- set-cell-blocked-boolean-array [arr cell*]
  (let [[x y] (:position cell*)]
    (aset arr x y (boolean (cell/blocked? cell* :z-order/flying)))))

(defn- ->cell-blocked-boolean-array [grid]
  (let [arr (make-array Boolean/TYPE (grid2d/width grid) (grid2d/height grid))]
    (doseq [cell (grid2d/cells grid)]
      (set-cell-blocked-boolean-array arr @cell))
    arr))

(defn- tiled-map->grid [tiled-map]
  (context.game.world.grid/->build (tiled/width  tiled-map)
                                   (tiled/height tiled-map)
                                   (fn [position]
                                     (case (movement-property tiled-map position)
                                       "none" :none
                                       "air"  :air
                                       "all"  :all))))

; TODO make defrecord
(defn- ->world-map [{:keys [tiled-map start-position] :as world-map}]
  (let [grid (tiled-map->grid tiled-map)
        w (grid2d/width  grid)
        h (grid2d/height grid)]
    (merge world-map
           {:width w
            :height h
            :grid grid
            :cell-blocked-boolean-array (->cell-blocked-boolean-array grid)
            :content-grid (context.game.world.content-grid/->build w h 16 16)
            :explored-tile-corners (atom (grid2d/create-grid w h (constantly false)))}))
  ; TODO
  ; (check-not-allowed-diagonals grid)
  )

(def ^:private spawn-enemies? true)

(defn- transact-create-entities-from-tiledmap! [{:keys [context.game/world] :as ctx}]
  (let [tiled-map (:tiled-map world)
        ctx (if spawn-enemies?
              (ctx/do! ctx
                       (for [[posi creature-id] (tiled/positions-with-property tiled-map :creatures :id)]
                         [:tx.entity/creature
                          (keyword creature-id)
                          #:entity {:position (tile->middle posi)
                                    :state [:state/npc :sleeping]}]))
              ctx)]
    (tiled/remove-layer! tiled-map :creatures)  ; otherwise will be rendered, is visible
    (ctx/do! ctx [[:tx.entity/creature
                   :creatures/vampire
                   #:entity {:position (tile->middle (:start-position world))
                             :state [:state/player :idle]
                             :player? true
                             :free-skill-points 3
                             :clickable {:type :clickable/player}
                             :click-distance-tiles 1.5}]])))

(defn- add-world-context [ctx tiled-level]
  (when-let [world (:context.game/world ctx)]
    (dispose (:tiled-map world)))
  (-> ctx
      (assoc :context.game/world (->world-map tiled-level))
      transact-create-entities-from-tiledmap!))

(defn- reset-world-context [ctx]
  (assoc ctx :context.game/world (->world-map (select-keys (:context.game/world ctx)
                                                           [:tiled-map
                                                            :start-position]))))

(defn- setup-context [ctx mode tiled-level]
  (case mode
    :game-loop/normal (add-world-context ctx tiled-level)
    :game-loop/replay (reset-world-context ctx)))

(defn- init-game-context [ctx & {:keys [mode record-transactions? tiled-level]}]
  (-> ctx
      (dissoc ::tick-error)
      (merge {:context.game/game-loop-mode mode}
             (ecs/->build)
             (time-component/->build)
             (widgets/->state! ctx)
             (tx-handler/initialize! mode record-transactions?))
      (setup-context mode tiled-level)))

(defn start-new-game [ctx tiled-level]
  (init-game-context ctx
                     :mode :game-loop/normal
                     :record-transactions? false ; TODO top level flag ?
                     :tiled-level tiled-level))

(defn- start-replay-mode! [ctx]
  (input/set-processor! nil)
  (init-game-context ctx :mode :game-loop/replay))

(def ^:private pausing? true)

(defn- player-unpaused? []
  (or (input/key-just-pressed? input.keys/p)
      (input/key-pressed?      input.keys/space)))

(defn- update-game-paused [ctx]
  (assoc ctx ::paused? (or (::tick-error ctx)
                           (and pausing?
                                (ctx/player-state-pause-game? ctx)
                                (not (player-unpaused?))))))

(extend-type api.context.Context
  api.context/Game
  (game-paused? [ctx]
    (::paused? ctx)))

(defn- active-entities [ctx]
  (content-grid/active-entities (ctx/content-grid ctx)
                                (ctx/player-entity* ctx)))

(defn- update-world [ctx]
  (let [ctx (time-component/update-time ctx)
        active-entities (active-entities ctx)]
    (ctx/update-potential-fields! ctx active-entities)
    (try (ctx/tick-entities! ctx active-entities)
         (catch Throwable t
           (p/pretty-pst t 12)
           (assoc ctx ::tick-error t)))))

(defmulti game-loop :context.game/game-loop-mode)

(defmethod game-loop :game-loop/normal [ctx]
  (ctx/do! ctx [ctx/player-update-state
                mouseover-entity/update! ; this do always so can get debug info even when game not running
                update-game-paused
                #(if (ctx/game-paused? %)
                   %
                   (update-world %))
                ctx/remove-destroyed-entities! ; do not pause this as for example pickup item, should be destroyed.
                ]))

(defn- replay-frame! [ctx]
  (let [frame-number (:context.game/logic-frame ctx)
        txs (ctx/frame->txs ctx frame-number)]
    ;(println frame-number ". " (count txs))
    (-> ctx
        (ctx/do! txs)
        (update :context.game/logic-frame inc))))

(def ^:private replay-speed 2)

(defmethod game-loop :game-loop/replay [ctx]
  (reduce (fn [ctx _] (replay-frame! ctx))
          ctx
          (range replay-speed)))

(defn- adjust-zoom [camera by] ; DRY map editor
  (orthographic-camera/set-zoom! camera (max 0.1 (+ (orthographic-camera/zoom camera) by))))

(def ^:private zoom-speed 0.05)

(defn- check-zoom-keys [context]
  (let [camera (ctx/world-camera context)]
    (when (input/key-pressed? input.keys/minus)  (adjust-zoom camera    zoom-speed))
    (when (input/key-pressed? input.keys/equals) (adjust-zoom camera (- zoom-speed)))))

; TODO move to actor/stage listeners ? then input processor used ....
(defn- check-key-input [context]
  (check-zoom-keys context)
  (widgets/check-window-hotkeys context)
  (cond (and (input/key-just-pressed? input.keys/escape)
             (not (widgets/close-windows? context)))
        (ctx/change-screen context :screens/options-menu)

        ; TODO not implementing StageSubScreen so NPE no screen/render!
        #_(input/key-just-pressed? input.keys/tab)
        #_(ctx/change-screen context :screens/minimap)

        :else
        context))

(defn- render-game! [ctx]
  (let [player-entity* (ctx/player-entity* ctx)]
    (camera/set-position! (ctx/world-camera ctx) (:position player-entity*))
    (ctx/render-map ctx)
    (ctx/render-world-view ctx
                           (fn [g]
                             (debug-render/before-entities ctx g)
                             (ctx/render-entities! ctx
                                                   g
                                                   (->> (active-entities ctx)
                                                        (map deref)
                                                        (filter :z-order)
                                                        (filter #(ctx/line-of-sight? ctx player-entity* %))))
                             (debug-render/after-entities ctx g)))))

(defn render [ctx]
  (render-game! ctx)
  (-> ctx
      game-loop
      check-key-input)) ; not sure I need this @ replay mode ??

(comment

 ; TODO @replay-mode
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

 (require 'app)
 (app/post-runnable
  (fn []
    (swap! app/state start-replay-mode!)))

 )
