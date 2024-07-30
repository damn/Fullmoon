(ns screens.main-menu
  (:require [core.component :as component]
            [utils.core :refer [safe-get tile->middle]]
            ;; api
            [gdl.app :refer [current-context change-screen!]]
            [api.context :as ctx :refer [exit-app ->text-button key-just-pressed? ->table ->actor ->tiled-map get-property rebuild-inventory-widgets reset-actionbar frame->txs transact-all! remove-destroyed-entities!]]
            [api.input.keys :as input.keys]
            [api.screen :as screen]
            ;; hardcoded

            ;; common game ctx
            context.ecs

           ; context.uids->entities ; 1. problem '->'
           ; context.thrown-error
           ; context.game-paused? ; 2. problem '?'
           ; context.game-logic-frame

            context.elapsed-game-time
            context.mouseover-entity
            context.player-message

            ;; other ctx
            [context.transaction-handler :as txs]
            [context.world :as world]
            mapgen.module-gen))

(defn- fetch-player-entity [ctx]
  {:post [%]}
  (first (filter #(:entity/player? @%) (api.context/all-entities ctx))))

(defn- ->player-entity-context [ctx]
  {:context/player-entity (fetch-player-entity ctx)})

; or just call 'build' with certain args
; and just those components merge with the original context
; game-context is just a list of keywords what parts are the game context
(defn- reset-common-game-context! [ctx]
  (rebuild-inventory-widgets ctx)
  (reset-actionbar ctx)
  (merge {:context/uids->entities (atom {})
          :context/thrown-error (atom nil)
          :context/game-paused? (atom nil)
          :context/game-logic-frame (atom 0)
          :context/elapsed-game-time (atom 0)
          :context/mouseover-entity (atom nil)
          :context/player-message (atom nil)}))

(defn- start-new-game [ctx tiled-level]
  (let [ctx (merge ctx
                   (reset-common-game-context! ctx)
                   {:context/replay-mode? false}
                   (world/->context ctx tiled-level))]
    ;(txs/clear-recorded-txs!)
    ;(txs/set-record-txs! true) ; TODO set in config ? ignores option menu setting and sets true always.
    (world/transact-create-entities-from-tiledmap! ctx)
    ;(println "Initial entity txs:")
    ;(txs/summarize-txs (frame->txs ctx 0))
    (merge ctx (->player-entity-context ctx))))

(defn- start-replay-mode! [ctx]
  (.setInputProcessor com.badlogic.gdx.Gdx/input nil)
  (txs/set-record-txs! false)
  ; remove entity connections to world grid/content-grid,
  ; otherwise all entities removed with reset-common-game-context!
  (transact-all! ctx (for [e (api.context/all-entities ctx)] [:tx/destroy e]))
  (remove-destroyed-entities! ctx)
  (let [ctx (merge ctx (reset-common-game-context! ctx))] ; without replay-mode / world ... make it explicit we re-use this here ? assign ?
    ; world visibility is not reset ... ...
    (transact-all! ctx (frame->txs ctx 0))
    (reset! gdl.app/current-context
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
                   @gdl.app/current-context)))

 )


(defn- ->vampire-tmx [context]
  {:tiled-map (->tiled-map context "maps/vampire.tmx")
   :start-position (tile->middle [32 71])})

(defn- ->rand-module-world [context]
  (let [{:keys [tiled-map
                start-position]} (mapgen.module-gen/generate
                                  context
                                  (get-property context :worlds/first-level))]
    {:tiled-map tiled-map
     :start-position (tile->middle start-position)}))

(defn- ->actors [{:keys [context/config] :as context} background-image]
  (let [table (->table context
                       {:rows (remove nil?
                                      [[(->text-button context "Start vampire.tmx" (fn [context]
                                                                                     (swap! current-context start-new-game (->vampire-tmx context))
                                                                                     (change-screen! :screens/game)))]
                                       [(->text-button context "Start procedural" (fn [context]
                                                                                    (swap! current-context start-new-game (->rand-module-world context))
                                                                                    (change-screen! :screens/game)))]

                                       (when (safe-get config :map-editor?)
                                         [(->text-button context "Map editor" (fn [_context]
                                                                                (change-screen! :screens/map-editor)))])
                                       (when (safe-get config :property-editor?)
                                         [(->text-button context "Property editor" (fn [_context]
                                                                                     (change-screen! :screens/property-editor)))])

                                       [(->text-button context "Exit" exit-app)]])
                        :cell-defaults {:pad-bottom 25}
                        :fill-parent? true})]
    [background-image
     table
     (->actor context {:act (fn [ctx]
                              (when (key-just-pressed? ctx input.keys/escape)
                                (exit-app ctx)))})]))

(component/def :screens/main-menu {}
  _
  (screen/create [_ ctx]
    (ctx/->stage-screen ctx
                        {:actors (->actors ctx (api.context/->background-image ctx))})))
