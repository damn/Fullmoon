(ns screens.options-menu
  (:require [core.component :as component]
            [app.state :refer [change-screen!]]
            [api.screen :as screen]
            [api.context :as ctx :refer [->text-button ->check-box key-just-pressed? ->table]]
            [api.input.keys :as input.keys]
            [utils.core :refer [safe-get]]
            context.transaction-handler
            context.render-debug
            context.world
            screens.game
            entity.body))

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
                                [#'entity.body/show-body-bounds
                                 ;#'context.transaction-handler/record-txs?
                                 #'context.transaction-handler/debug-print-txs?
                                 #'context.render-debug/tile-grid?
                                 #'context.render-debug/cell-occupied?
                                 #'context.render-debug/highlight-blocked-cell?
                                 #'context.render-debug/cell-entities?
                                 #'context.render-debug/potential-field-colors?
                                 #'screens.game/pausing?
                                 #'context.world/los-checks?
                                 #'context.world/spawn-enemies?
                                 #'world.render/see-all-tiles?]))

(defn- exit []
  (change-screen! :screens/game))

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
                   [[(->text-button ctx "Resume" (fn [_ctx] (exit)))]
                    [(->text-button ctx "Exit" (fn [_ctx] (change-screen! :screens/main-menu)))]])
            :fill-parent? true
            :cell-defaults {:pad-bottom 10}}))

(deftype SubScreen []
  api.screen/Screen
  (show [_ _ctx])
  (hide [_ _ctx])
  (render [_ ctx]
    (when (key-just-pressed? ctx input.keys/escape)
      (exit))))

(defn- ->screen [ctx background-image]
  {:actors [background-image
            (create-table ctx)]
   :sub-screen (->SubScreen)})

(component/def :screens/options-menu {}
  _
  (screen/create [_ ctx] (ctx/->stage-screen ctx (->screen ctx (api.context/->background-image ctx)))))
