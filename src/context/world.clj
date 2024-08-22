(ns context.world
  (:require [clj-commons.pretty.repl :as p]
            [gdx.app :as app]
            [gdx.input :as input]
            [gdx.input.keys :as input.keys]
            [gdx.utils.disposable :refer [dispose]]
            [utils.core :refer [tile->middle]]
            [core.component :as component :refer [defcomponent]]
            [data.grid2d :as grid2d]
            [api.context :as ctx]
            [api.entity :as entity]
            [api.effect :as effect]
            [api.maps.tiled :as tiled]
            [api.world.grid :as world-grid]
            [api.world.content-grid :as content-grid]
            [api.world.cell :as cell]))

(def ^:private ^:dbg-flag spawn-enemies? true)

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

(defn- create-components [ctx components]
  (component/strict-update components component/create ctx))

(defn- ->world-map [{:keys [tiled-map start-position] :as world-map}]
  (create-components
   #:world {:tiled-map tiled-map
            :start-position start-position}
   #:world {:grid [(tiled/width tiled-map)
                   (tiled/height tiled-map)
                   #(case (tiled/movement-property tiled-map %)
                      "none" :none
                      "air"  :air
                      "all"  :all)]
            :raycaster #(cell/blocked? % :z-order/flying)
            :content-grid [16 16]
            :explored-tile-corners true})
  ; TODO (check-not-allowed-diagonals grid)
  )

(defn- init-game-context [ctx & {:keys [mode record-transactions? tiled-level]}]
  (let [ctx (-> ctx
                (dissoc ::tick-error)
                (merge {::game-loop-mode mode}
                       (create-components ctx
                                          #:world {:ecs true
                                                   :time true
                                                   :widgets true
                                                   :effect-handler [mode record-transactions?]})))]
    (case mode
      :game-loop/normal (do
                         (when-let [tiled-map (:world/tiled-map ctx)]
                           (dispose tiled-map))
                         (-> ctx
                             (merge (->world-map tiled-level))
                             transact-create-entities-from-tiledmap!))
      :game-loop/replay (merge ctx (->world-map (select-keys ctx [:world/tiled-map :world/start-position]))))))

(defn- start-replay-mode! [ctx]
  (input/set-processor! nil)
  (init-game-context ctx :mode :game-loop/replay))

(extend-type api.context.Context
  api.context/World
  (start-new-game [ctx tiled-level]
    (init-game-context ctx
                       :mode :game-loop/normal
                       :record-transactions? false ; TODO top level flag ?
                       :tiled-level tiled-level))

  ; TODO these two what to do ?
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

(def ^:private ^:dbg-flag pausing? true)

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
  (let [ctx (ctx/update-time ctx)
        active-entities (active-entities ctx)]
    (ctx/update-potential-fields ctx active-entities)
    (try (ctx/tick-entities! ctx active-entities)
         (catch Throwable t
           (p/pretty-pst t 12)
           (assoc ctx ::tick-error t)))))

(defmulti game-loop ::game-loop-mode)

(defmethod game-loop :game-loop/normal [ctx]
  (ctx/do! ctx [ctx/player-update-state
                ctx/update-mouseover-entity ; this do always so can get debug info even when game not running
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
