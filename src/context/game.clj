(ns context.game
  (:require [core.component :refer [defcomponent] :as component]
            [api.context :as ctx]
            [api.entity :as entity]
            [api.entity-state :as state]
            [api.graphics.camera :as camera]
            [api.input.keys :as input.keys]
            [api.world.content-grid :as content-grid]
            app.state
            [context.world :as world]
            context.game-widgets
            [debug.render :as debug-render]
            [entity.movement :as movement]))

; TODO make game context contain all those things
; not in main context ...... ?
; e.g. game-paused ?

(defcomponent :context/game {}
  (component/create [_ _ctx]
    [;; widgets load before context/game-widgets
     :context/inventory
     :context/player-message
     :context/game-widgets
     ;;
     :context/uids-entities ; move to ecs ?
     :context/thrown-error  ; move to ecs ?
     :context/game-paused ; only used in this ns
     :context/game-logic-frame
     :context/elapsed-game-time
     :context/mouseover-entity]))

(defn- merge-rebuild-game-context [{:keys [context/game] :as ctx}]
  (let [components (map #(vector % nil) game)]
    (component/load! components)
    (reduce (fn [ctx {k 0 :as component}]
              (assoc ctx k (component/create component ctx)))
            ctx
            components)))

(defn- start-new-game [ctx tiled-level]
  (let [ctx (merge (merge-rebuild-game-context ctx)
                   {:context/replay-mode? false}
                   (world/->context ctx tiled-level))]

    ;(ctx/clear-recorded-txs! ctx)
    ;(ctx/set-record-txs! ctx true) ; TODO set in config ? ignores option menu setting and sets true always.

    (let [player-entity (world/transact-create-entities-from-tiledmap! ctx)]
      ;(println "Initial entity txs:")
      ;(ctx/summarize-txs ctx (ctx/frame->txs ctx 0))
      (assoc ctx :context/player-entity player-entity))))

(defn- start-replay-mode! [ctx]
  (.setInputProcessor com.badlogic.gdx.Gdx/input nil)
  (ctx/set-record-txs! ctx false)

  ; remove entity connections to world grid/content-grid,
  ; otherwise all entities removed with ->context
  (ctx/transact-all! ctx (for [e (api.context/all-entities ctx)] [:tx/destroy e]))
  (ctx/remove-destroyed-entities! ctx)

  (let [ctx (merge-rebuild-game-context ctx)] ; without replay-mode / world ... make it explicit we re-use this here ? assign ?
    ; world visibility is not reset ... ...
    (ctx/transact-all! ctx (ctx/frame->txs ctx 0))

    (reset! app.state/current-context
            (merge ctx {:context/replay-mode? true}))))

(defn- adjust-zoom [camera by] ; DRY map editor
  (camera/set-zoom! camera (max 0.1 (+ (camera/zoom camera) by))))

(def ^:private zoom-speed 0.05)

(defn- check-zoom-keys [context]
  (let [camera (ctx/world-camera context)]
    (when (ctx/key-pressed? context input.keys/minus)  (adjust-zoom camera    zoom-speed))
    (when (ctx/key-pressed? context input.keys/equals) (adjust-zoom camera (- zoom-speed)))))

; TODO move to actor/stage listeners ? then input processor used ....
(defn- check-key-input [context]
  (check-zoom-keys context)
  (context.game-widgets/check-window-hotkeys context)
  (when (and (ctx/key-just-pressed? context input.keys/escape)
             (not (context.game-widgets/close-windows? context)))
    (app.state/change-screen! :screens/options-menu))
  (when (ctx/key-just-pressed? context input.keys/tab)
    (app.state/change-screen! :screens/minimap)))

(defn- render-game [{:keys [context/player-entity] :as context} active-entities*]
  (camera/set-position! (ctx/world-camera context)
                        (:entity/position @player-entity))
  (ctx/render-map context)
  (ctx/render-world-view context
                         (fn [g]
                           (debug-render/before-entities context g)
                           (ctx/render-entities! context
                                                 g
                                                 ; TODO lazy seqS everywhere!
                                                 (->> active-entities*
                                                      (filter :entity/z-order)
                                                      (filter #(ctx/line-of-sight? context @player-entity %))))
                           (debug-render/after-entities context g))))

(def ^:private pausing? true)

(defn- player-unpaused? [ctx]
  (or (ctx/key-just-pressed? ctx input.keys/p)
      (ctx/key-pressed?      ctx input.keys/space)))

(defn- assoc-delta-time [ctx]
  (assoc ctx :context/delta-time (min (ctx/delta-time ctx) movement/max-delta-time)))

(defn- update-game [{:keys [context/player-entity
                            context/game-paused
                            context/thrown-error
                            context/game-logic-frame]
                     :as ctx}
                    active-entities]
  (let [state-obj (entity/state-obj @player-entity)
        _ (ctx/transact-all! ctx (state/manual-tick state-obj @player-entity ctx))
        paused? (reset! game-paused (or @thrown-error
                                        (and pausing?
                                             (state/pause-game? (entity/state-obj @player-entity))
                                             (not (player-unpaused? ctx)))))
        ctx (assoc-delta-time ctx)]
    (ctx/update-mouseover-entity! ctx) ; this do always so can get debug info even when game not running
    (when-not paused?
      (swap! game-logic-frame inc)
      (ctx/update-elapsed-game-time! ctx)
      (ctx/update-potential-fields! ctx active-entities)
      (ctx/tick-entities! ctx (map deref active-entities))) ; TODO lazy seqs everywhere!
    (ctx/remove-destroyed-entities! ctx) ; do not pause this as for example pickup item, should be destroyed.
    (check-key-input ctx)))

(defn- replay-frame! [ctx frame-number]
  (ctx/update-mouseover-entity! ctx)
  (ctx/update-elapsed-game-time! (assoc-delta-time ctx))
  (let [txs (ctx/frame->txs ctx frame-number)]
    ;(println frame-number ". " (count txs))
    (ctx/transact-all! ctx txs))
  (check-key-input ctx))

; TODO adjust sound speed also equally ? pitch ?
(def ^:private replay-speed 2)

(defn- replay-game! [{:keys [context/game-logic-frame] :as ctx}]
  (dotimes [_ replay-speed]
    (replay-frame! ctx (swap! game-logic-frame inc))))

(extend-type api.context.Context
  api.context/Game
  (start-new-game [ctx tiled-level]
    (start-new-game ctx tiled-level))

  (render-game [{:keys [context/player-entity
                        context/replay-mode?] :as context}]
    (let [active-entities (content-grid/active-entities (ctx/content-grid context) player-entity)]
      ; TODO lazy seqS everywhere!
      (render-game context (map deref active-entities))
      (if replay-mode?
        (replay-game! context)
        (update-game context active-entities)))))

(comment

 ; explored-tiles? (TODO)
 ; player message, player modals, etc. all game related state handle ....
 ; game timer is not reset  - continues as if
 ; entities all disappearing, just stop when end reached ....
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

 ; need to set this @ start-new-game for recording of txs for this to work..
 ;(ctx/clear-recorded-txs! ctx)
 ;(ctx/set-record-txs! ctx true) ; TODO set in config ? ignores option menu setting and sets true always.
 (.postRunnable com.badlogic.gdx.Gdx/app
                (fn []
                  (start-replay-mode!
                   @app.state/current-context)))

 )
