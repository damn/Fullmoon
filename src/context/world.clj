(ns context.world
  (:require [clj-commons.pretty.repl :as p]

            [gdx.app :as app]
            [gdx.graphics.camera :as camera]
            [gdx.input :as input]
            [gdx.input.keys :as input.keys]
            [gdx.utils.disposable :refer [dispose]]

            [utils.core :refer [tile->middle]]

            [core.component :refer [defcomponent]]

            [data.grid2d :as grid2d]

            [api.context :as ctx]
            [api.entity :as entity]
            [api.effect :as effect]
            [api.maps.tiled :as tiled]
            [api.world.grid :as world-grid]
            [api.world.content-grid :as content-grid]
            [api.world.cell :as cell]

            (world grid
                   content-grid
                   [potential-fields :as potential-fields]
                   render
                   [raycaster :as raycaster]
                   [ecs :as ecs]
                   [mouseover-entity :as mouseover-entity]
                   [time :as time-component]
                   [effect-handler :as tx-handler]
                   [widgets :as widgets])

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

(def ^:private los-checks? true)

(extend-type api.context.Context
  api.context/World
  (line-of-sight? [context source* target*]
    (and (:z-order target*)  ; is even an entity which renders something
         (or (not (:entity/player? source*))
             (on-screen? target* context))
         (not (and los-checks?
                   (ctx/ray-blocked? context (:position source*) (:position target*))))))

  ; TODO put tile param
  (explored? [ctx position]
    (get @(:world/explored-tile-corners ctx) position))

  (content-grid [ctx] (:world/content-grid ctx))
  (world-grid  [ctx]  (:world/grid         ctx)))

(defcomponent :tx/add-to-world {}
  (effect/do! [[_ entity] ctx]
    (content-grid/update-entity! (ctx/content-grid ctx) entity)
    ; hmm
    ;(assert (valid-position? grid @entity)) ; TODO deactivate because projectile no left-bottom remove that field or update properly for all
    (world-grid/add-entity! (ctx/world-grid ctx) entity)
    ctx))

(defcomponent :tx/remove-from-world {}
  (effect/do! [[_ entity] ctx]
    (content-grid/remove-entity! (ctx/content-grid ctx) entity)
    (world-grid/remove-entity! (ctx/world-grid ctx) entity)
    ctx))

(defcomponent :tx/position-changed {}
  (effect/do! [[_ entity] ctx]
    (content-grid/update-entity! (ctx/content-grid ctx) entity)
    (world-grid/entity-position-changed! (ctx/world-grid ctx) entity)
    ctx))

(defn- tiled-map->world-grid [tiled-map]
  (world.grid/->build (tiled/width  tiled-map)
                      (tiled/height tiled-map)
                      (fn [position]
                        (case (movement-property tiled-map position)
                          "none" :none
                          "air"  :air
                          "all"  :all))))

(defn- ->world-map [{:keys [tiled-map start-position] :as world-map}]
  (let [grid (tiled-map->world-grid tiled-map)
        w (grid2d/width  grid)
        h (grid2d/height grid)]
    #:world {:tiled-map tiled-map
             :start-position start-position
             :grid grid
             :raycaster (raycaster/->build grid #(cell/blocked? % :z-order/flying))
             :content-grid (world.content-grid/->build w h 16 16)
             :explored-tile-corners (atom (grid2d/create-grid w h (constantly false)))})
  ; TODO
  ; (check-not-allowed-diagonals grid)
  )

(def ^:private spawn-enemies? true)

(defn- transact-create-entities-from-tiledmap! [{:keys [world/tiled-map world/start-position] :as ctx}]
  (let [ctx (if spawn-enemies?
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
                   #:entity {:position (tile->middle start-position)
                             :state [:state/player :idle]
                             :player? true
                             :free-skill-points 3
                             :clickable {:type :clickable/player}
                             :click-distance-tiles 1.5}]])))

(defn- add-world-context [ctx tiled-level]
  (when-let [tiled-map (:world/tiled-map ctx)]
    (dispose tiled-map))
  (-> ctx
      (merge (->world-map tiled-level))
      transact-create-entities-from-tiledmap!))

(defn- reset-world-context [ctx]
  (merge ctx (->world-map (select-keys ctx [:world/tiled-map :world/start-position]))))

(defn- setup-context [ctx mode tiled-level]
  (case mode
    :game-loop/normal (add-world-context ctx tiled-level)
    :game-loop/replay (reset-world-context ctx)))

(defn- init-game-context [ctx & {:keys [mode record-transactions? tiled-level]}]
  (-> ctx
      (dissoc ::tick-error)
      (merge {::game-loop-mode mode}
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

(defn active-entities [ctx]
  (content-grid/active-entities (ctx/content-grid ctx)
                                (ctx/player-entity* ctx)))

(defn- update-world [ctx]
  (let [ctx (time-component/update-time ctx)
        active-entities (active-entities ctx)]
    (potential-fields/update! (ctx/world-grid ctx) active-entities)
    (try (ctx/tick-entities! ctx active-entities)
         (catch Throwable t
           (p/pretty-pst t 12)
           (assoc ctx ::tick-error t)))))

(defmulti game-loop ::game-loop-mode)

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

(comment

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

 (require 'app)
 (app/post-runnable
  (fn []
    (swap! app/state start-replay-mode!)))

 )
