(ns context.game
  (:require [app.state :refer [current-context]]
            [api.context :as ctx]
            context.ecs
            context.elapsed-game-time
            context.mouseover-entity
            context.player-message
            [context.transaction-handler :as txs]
            [context.world :as world]))

; 1.
; initialize game context @ app
; context.uids->entities ; 1. problem '->'
; context.thrown-error
; context.game-paused? ; 2. problem '?'
; context.game-logic-frame

; 2.
; call a context function - ctx/reset-game on all context components
; which implement that -> main menu doesn't need to know any details !

(defn- fetch-player-entity [ctx]
  {:post [%]}
  (first (filter #(:entity/player? @%) (api.context/all-entities ctx))))

(defn- ->player-entity-context [ctx]
  {:context/player-entity (fetch-player-entity ctx)})

; or just call 'build' with certain args
; and just those components merge with the original context
; game-context is just a list of keywords what parts are the game context

; TODO values need to be @ the component defined !!
; not here -
(defn- reset-common-game-context! [ctx]
  (ctx/rebuild-inventory-widgets ctx)
  (ctx/reset-actionbar ctx)
  (merge {:context/uids->entities (atom {})
          :context/thrown-error (atom nil)
          :context/game-paused? (atom nil)
          :context/game-logic-frame (atom 0)
          :context/elapsed-game-time (atom 0)
          :context/mouseover-entity (atom nil)
          :context/player-message (atom nil)}))

(extend-type api.context.Context
  api.context/Game
  (start-new-game [ctx tiled-level]
    (let [ctx (merge ctx
                     (reset-common-game-context! ctx)
                     {:context/replay-mode? false}
                     (world/->context ctx tiled-level))]
      ;(txs/clear-recorded-txs!)
      ;(txs/set-record-txs! true) ; TODO set in config ? ignores option menu setting and sets true always.
      (world/transact-create-entities-from-tiledmap! ctx)
      ;(println "Initial entity txs:")
      ;(txs/summarize-txs (ctx/frame->txs ctx 0))
      (merge ctx (->player-entity-context ctx)))))

(defn- start-replay-mode! [ctx]
  (.setInputProcessor com.badlogic.gdx.Gdx/input nil)
  (txs/set-record-txs! false)
  ; remove entity connections to world grid/content-grid,
  ; otherwise all entities removed with reset-common-game-context!
  (ctx/transact-all! ctx (for [e (api.context/all-entities ctx)] [:tx/destroy e]))
  (ctx/remove-destroyed-entities! ctx)
  (let [ctx (merge ctx (reset-common-game-context! ctx))] ; without replay-mode / world ... make it explicit we re-use this here ? assign ?
    ; world visibility is not reset ... ...
    (ctx/transact-all! ctx (ctx/frame->txs ctx 0))
    (reset! app.state/current-context
            (merge ctx
                   (->player-entity-context ctx)
                   {:context/replay-mode? true}))))

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

 (.postRunnable com.badlogic.gdx.Gdx/app
                (fn []
                  (start-replay-mode!
                   @app.state/current-context)))

 )
