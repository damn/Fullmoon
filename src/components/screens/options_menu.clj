(ns components.screens.options-menu
  (:require [gdx.input :as input]
            [gdx.input.keys :as input.keys]
            [utils.core :refer [safe-get]]
            utils.ns
            [core.component :refer [defcomponent] :as component]
            [core.context :as ctx :refer [->text-button ->check-box ->table]]
            core.screen))

(defprotocol StatusCheckBox
  (get-text [this])
  (get-state [this])
  (set-state [this is-selected]))

(deftype VarStatusCheckBox [^clojure.lang.Var avar]
  StatusCheckBox
  (get-text [this]
    (let [m (meta avar)]
      (str "[LIGHT_GRAY]" (str (:ns m)) "/[WHITE]" (name (:name m)) "[]")))

  (get-state [this]
    @avar)

  (set-state [this is-selected]
    (.bindRoot avar is-selected)))

(defn- debug-flags []
  (apply concat
         (for [nmspace (utils.ns/get-namespaces #{"components"})]
           (utils.ns/get-vars nmspace (fn [avar] (:dbg-flag (meta avar)))))))

; TODO FIXME IF THE FLAGS ARE CHANGED MANUALLY IN THE REPL THIS IS NOT REFRESHED
; -. rebuild it on window open ...
(def ^:private debug-flags (map ->VarStatusCheckBox (debug-flags)))

(def ^:private key-help-text
  "[W][A][S][D] - Move\n[I] - Inventory window\n[E] - Entity Info window\n[-]/[=] - Zoom\n[TAB] - Minimap\n[P]/[SPACE] - Unpause")

(defn- create-table [{:keys [context/config] :as ctx}]
  (->table ctx
           {:rows (concat
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
  core.screen/Screen
  (show [_ _ctx])
  (hide [_ _ctx])
  (render [_ ctx]
    (if (input/key-just-pressed? input.keys/escape)
      (ctx/change-screen ctx :screens/world)
      ctx)))

(defcomponent :screens/options-menu {}
  (component/create [_ ctx]
    (ctx/->stage-screen ctx
                        {:actors [(ctx/->background-image ctx)
                                  (create-table ctx)]
                         :sub-screen (->SubScreen)})))
