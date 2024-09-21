(ns core.context.screens.options-menu
  (:require [utils.core :refer [safe-get]]
            utils.ns
            [gdx.scene2d.ui :as ui]
            [core.component :refer [defcomponent] :as component]
            [core.context :as ctx]
            [core.context.screens :as screens]
            [core.screen :as screen])
  (:import (com.badlogic.gdx Gdx Input$Keys)))

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
         ; TODO
         (for [nmspace (utils.ns/get-namespaces #{"core"})] ; DRY in core.component check ns-name & core.app require all ... core.components
           (utils.ns/get-vars nmspace (fn [avar] (:dbg-flag (meta avar)))))))

; TODO FIXME IF THE FLAGS ARE CHANGED MANUALLY IN THE REPL THIS IS NOT REFRESHED
; -. rebuild it on window open ...
(def ^:private debug-flags (map ->VarStatusCheckBox (debug-flags)))

(def ^:private key-help-text
  "[W][A][S][D] - Move\n[I] - Inventory window\n[E] - Entity Info window\n[-]/[=] - Zoom\n[TAB] - Minimap\n[P]/[SPACE] - Unpause")

(defn- create-table [{:keys [context/config] :as ctx}]
  (ui/->table {:rows (concat
                      [[(ui/->label key-help-text)]]

                      (when (safe-get config :debug-window?)
                        [[(ui/->label "[Z] - Debug window")]])

                      (when (safe-get config :debug-options?)
                        (for [check-box debug-flags]
                          [(ui/->check-box (get-text check-box)
                                           (partial set-state check-box)
                                           (boolean (get-state check-box)))]))

                      [[(ui/->text-button ctx "Resume" #(screens/change-screen % :screens/world))]

                       [(ui/->text-button ctx "Exit" #(screens/change-screen % :screens/main-menu))]])

               :fill-parent? true
               :cell-defaults {:pad-bottom 10}}))

(defcomponent ::sub-screen
  (screen/render [_ ctx]
    (if (.isKeyJustPressed Gdx/input Input$Keys/ESCAPE)
      (screens/change-screen ctx :screens/world)
      ctx)))

(derive :screens/options-menu :screens/stage-screen)
(defcomponent :screens/options-menu
  (component/create [_ ctx]
    {:stage (ctx/->stage ctx
                         [(ctx/->background-image ctx)
                          (create-table ctx)])
     :sub-screen [::sub-screen]}))
