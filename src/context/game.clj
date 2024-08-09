(ns context.game
  (:require [api.context :as ctx]
            [api.entity :as entity]
            [api.entity-state :as state]
            [api.graphics.camera :as camera]
            [api.input.keys :as input.keys]
            [api.world.content-grid :as content-grid]

            [context.game.time :as time-component]
            [context.game.ecs :as ecs]
            [context.game.mouseover-entity :as mouseover-entity]
            context.game.player-entity
            context.game.transaction-handler
            [context.game.widgets :as widgets]
            [context.world :as world]

            [debug.render :as debug-render]))

(defn- merge-new-game-context [ctx & {:keys [mode]}]
  (merge ctx
         {:context.game/game-loop-mode mode}
         (time-component/->build)
         (context.game.player-entity/->state) ; not needed nil ....
         (ecs/->state)
         (widgets/->state! ctx)))

(defn start-new-game [ctx tiled-level]
  (let [ctx (merge (merge-new-game-context ctx :mode :game-loop/normal)
                   (world/->context ctx tiled-level))
        _ (ctx/clear-recorded-txs! ctx)
        _ (ctx/set-record-txs! ctx true) ; TODO set in config ? ignores option menu setting and sets true always.
        ctx (world/transact-create-entities-from-tiledmap! ctx)]
    (assert (:context.game/player-entity ctx))
    ;(println "Initial entity txs:")
    ;(ctx/summarize-txs ctx (ctx/frame->txs ctx 0))
    ctx))

(defn- start-replay-mode! [ctx]
  (assert @#'context.game.transaction-handler/record-txs?)
  (.setInputProcessor com.badlogic.gdx.Gdx/input nil)
  (ctx/set-record-txs! ctx false)
  ; keeping context/world !
  ; world visibility is not reset ... ...
  ; remove entity connections to world grid/content-grid,
  ; otherwise all entities removed with ->context
  (-> ctx
      (ctx/transact-all! (for [entity (ctx/all-entities ctx)]
                           [:tx/destroy entity]))
      ctx/remove-destroyed-entities!
      (merge-new-game-context :mode :game-loop/replay)
      (ctx/transact-all! (ctx/frame->txs ctx 0)))) ; TODO using old ctx value ... ??

(def ^:private pausing? true)

(defn- player-unpaused? [ctx]
  (or (ctx/key-just-pressed? ctx input.keys/p)
      (ctx/key-pressed?      ctx input.keys/space)))


(defn- player-manual-state-tick [ctx]
  (let [entity* (ctx/player-entity* ctx)]
    (state/manual-tick (entity/state-obj entity*) entity* ctx)))

(defmulti game-loop :context.game/game-loop-mode)

(defmethod game-loop :game-loop/normal [ctx active-entities]
  (let [ctx (ctx/transact-all! ctx (player-manual-state-tick ctx))
        paused? (or (ctx/entity-error ctx)
                    (and pausing?
                         (state/pause-game? (entity/state-obj (ctx/player-entity* ctx)))
                         (not (player-unpaused? ctx))))
        ctx (-> ctx
                (assoc :context.game/paused? paused?)
                mouseover-entity/update!) ; this do always so can get debug info even when game not running
        ctx (if paused?
              ctx
              (let [ctx (time-component/update-time ctx)]
                (ctx/update-potential-fields! ctx active-entities) ; TODO here pass entity*'s then I can deref @ render-game main fn ....
                (ctx/tick-entities! ctx (map deref active-entities))))]
    (ctx/remove-destroyed-entities! ctx))) ; do not pause this as for example pickup item, should be destroyed.

(defn- replay-frame! [ctx]
  (let [ctx (-> ctx

                (update :context.game/logic-frame inc) ; TODO fixme not using time-component here ???

                ; delta-time we don't need: movement & animation are in txs the info
                ;(mouseover-entity/update! ctx) dont need?!
                ;elapsed-time/update-time  ; jut for interest? but not needed?
                )
        frame-number (:context.game/logic-frame ctx)
        txs (ctx/frame->txs ctx frame-number)]
    ;(println frame-number ". " (count txs))
    (ctx/transact-all! ctx txs)))

; TODO adjust sound speed also equally ? pitch ?
(def ^:private replay-speed 1)

(defmethod game-loop :game-loop/replay [ctx _active-entities]
  (reduce (fn [ctx _] (replay-frame! ctx))
          ctx
          (range replay-speed)))

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
  (widgets/check-window-hotkeys context)
  (cond (and (ctx/key-just-pressed? context input.keys/escape)
             (not (widgets/close-windows? context)))
        (ctx/change-screen context :screens/options-menu)

        ; TODO not implementing StageSubScreen so NPE no screen/render!
        #_(ctx/key-just-pressed? context input.keys/tab)
        #_(ctx/change-screen context :screens/minimap)

        :else
        context))

(defn- render-game! [ctx active-entities*]
  (let [player-entity* (ctx/player-entity* ctx)]
    (camera/set-position! (ctx/world-camera ctx)
                          (:entity/position player-entity*))
    (ctx/render-map ctx)
    (ctx/render-world-view ctx
                           (fn [g]
                             (debug-render/before-entities ctx g)
                             (ctx/render-entities! ctx
                                                   g
                                                   (->> active-entities*
                                                        (filter :entity/z-order)
                                                        (filter #(ctx/line-of-sight? ctx player-entity* %))))
                             (debug-render/after-entities ctx g)))))


(defn render [ctx]
  (let [active-entities (content-grid/active-entities (ctx/content-grid ctx)
                                                      (ctx/player-entity* ctx))]
    (render-game! ctx (map deref active-entities))
    (-> ctx
        (game-loop active-entities)
        check-key-input))) ; not sure I need this @ replay mode ??

(extend-type api.context.Context
  api.context/Game
  (player-entity* [ctx]
    (let [entity (:context.game/player-entity ctx)]
      (assert entity)
      @entity)))

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

 (require 'app.state)
 (.postRunnable com.badlogic.gdx.Gdx/app (fn []
                                           (swap! app.state/current-context start-replay-mode!)))

 )
