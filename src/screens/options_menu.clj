(ns screens.options-menu
  (:require [gdx.input :as input]
            [core.component :refer [defcomponent]]
            [api.screen :as screen]
            [api.context :as ctx :refer [->text-button ->check-box ->table]]
            [gdx.input.keys :as input.keys]
            [utils.core :refer [safe-get]]
            world.effect-handler
            [world.debug-render :as debug-render]
            context.world
            world.ecs
            world.line-of-sight
            world.render))

(defprotocol StatusCheckBox
  (get-text [this])
  (get-state [this])
  (set-state [this is-selected]))

#_(def status-check-boxes (atom []))

#_(defmacro status-check-box [& forms]
  `(swap! status-check-boxes conj (reify StatusCheckBox ~@forms)))

#_(status-check-box
  (get-text [this] "Sound")
  (get-state [this] #_(.isSoundOn app-game-container))
  (set-state [this is-selected] #_(.setSoundOn app-game-container is-selected)))

(defn- ->debug-flag [avar]
  (reify StatusCheckBox
    (get-text [this]
      (let [m (meta avar)]
        (str "[LIGHT_GRAY]" (str (:ns m)) "/[WHITE]" (name (:name m)))))
    (get-state [this]
      @avar)
    (set-state [this is-selected]
      (.bindRoot ^clojure.lang.Var avar is-selected))))

; TODO add line of sight activate, shadows on/off, see through walls etc.
; TODO FIXME IF THE FLAGS ARE CHANGED MANUALLY IN THE REPL THIS IS NOT REFRESHED
(def ^:private debug-flags (map ->debug-flag
                                [#'world.ecs/show-body-bounds
                                 ;#'world.effect-handler/record-txs?
                                 #'world.effect-handler/debug-print-txs?
                                 #'debug-render/tile-grid?
                                 #'debug-render/cell-occupied?
                                 #'debug-render/highlight-blocked-cell?
                                 #'debug-render/cell-entities?
                                 #'debug-render/potential-field-colors?
                                 #'context.world/pausing?
                                 #'world.line-of-sight/los-checks?
                                 #'context.world/spawn-enemies?
                                 #'world.render/see-all-tiles?]))

(def ^:private key-help-text
  "[W][A][S][D] - Move\n[I] - Inventory window\n[E] - Entity Info window\n[-]/[=] - Zoom\n[TAB] - Minimap\n[P]/[SPACE] - Unpause")

(defn- create-table [{:keys [context/config] :as ctx}]
  (->table ctx
           {:rows (concat
                   #_(for [check-box @status-check-boxes]
                       [(->check-box ctx
                                     (get-text check-box)
                                     #(set-state check-box %)
                                     (boolean (get-state check-box)))])
                   [[(ctx/->label ctx key-help-text)]]
                   (when (safe-get config :debug-window?)
                     [[(ctx/->label ctx "[Z] - Debug window")]])
                   (when (safe-get config :debug-options?)
                     (for [check-box debug-flags]
                       [(->check-box ctx
                                     (get-text check-box)
                                     (partial set-state check-box)
                                     (boolean (get-state check-box)))]))
                   [[(->text-button ctx "Resume" #(ctx/change-screen % :screens/world))]
                    [(->text-button ctx "Exit" #(ctx/change-screen % :screens/main-menu))]])
            :fill-parent? true
            :cell-defaults {:pad-bottom 10}}))

(deftype SubScreen []
  api.screen/Screen
  (show [_ _ctx])
  (hide [_ _ctx])
  (render [_ ctx]
    (if (input/key-just-pressed? input.keys/escape)
      (ctx/change-screen ctx :screens/world)
      ctx)))

(defn- ->screen [ctx background-image]
  {:actors [background-image
            (create-table ctx)]
   :sub-screen (->SubScreen)})

(defcomponent :screens/options-menu {}
  (screen/create [_ ctx] (ctx/->stage-screen ctx (->screen ctx (ctx/->background-image ctx)))))
