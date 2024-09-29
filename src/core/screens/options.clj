(in-ns 'core.screens)

(defprotocol ^:private StatusCheckBox
  (^:private get-text [this])
  (^:private get-state [this])
  (^:private set-state [this is-selected]))

(deftype VarStatusCheckBox [^clojure.lang.Var avar]
  StatusCheckBox
  (get-text [this]
    (let [m (meta avar)]
      (str "[LIGHT_GRAY]" (str (:ns m)) "/[WHITE]" (name (:name m)) "[]")))

  (get-state [this]
    @avar)

  (set-state [this is-selected]
    (.bindRoot avar is-selected)))

; TODO not using clojure.ctx ns ... only core

(defn- debug-flags [] ;
  (apply concat
         ; TODO
         (for [nmspace (get-namespaces #{"core"})] ; DRY in core.component check ns-name & core.app require all ... core.components
           (get-vars nmspace (fn [avar] (:dbg-flag (meta avar)))))))

; TODO FIXME IF THE FLAGS ARE CHANGED MANUALLY IN THE REPL THIS IS NOT REFRESHED
; -. rebuild it on window open ...
(def ^:private debug-flags (map ->VarStatusCheckBox (debug-flags)))

(def ^:private key-help-text
  "[W][A][S][D] - Move\n[I] - Inventory window\n[E] - Entity Info window\n[-]/[=] - Zoom\n[TAB] - Minimap\n[P]/[SPACE] - Unpause")

(defn- create-table [{:keys [context/config] :as ctx}]
  (->table {:rows (concat
                   [[(->label key-help-text)]]

                   (when (safe-get config :debug-window?)
                     [[(->label "[Z] - Debug window")]])

                   (when (safe-get config :debug-options?)
                     (for [check-box debug-flags]
                       [(->check-box (get-text check-box)
                                     (partial set-state check-box)
                                     (boolean (get-state check-box)))]))

                   [[(->text-button "Resume" #(change-screen % :screens/world))]

                    [(->text-button "Exit" #(change-screen % :screens/main-menu))]])

            :fill-parent? true
            :cell-defaults {:pad-bottom 10}}))

(defcomponent :options/sub-screen
  (screen-render [_ ctx]
    (if (key-just-pressed? :keys/escape)
      (change-screen ctx :screens/world)
      ctx)))

(derive :screens/options-menu :screens/stage)
(defcomponent :screens/options-menu
  (->mk [_ ctx]
    {:stage (->stage ctx [(->background-image ctx)
                          (create-table ctx)])
     :sub-screen [:options/sub-screen]}))
