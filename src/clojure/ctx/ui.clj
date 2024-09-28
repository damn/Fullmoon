(in-ns 'clojure.ctx)

(defcomponent :context/vis-ui
  {:data [:enum [:skin-scale/x1 :skin-scale/x2]]
   :let skin-scale}
  (->mk [_ _ctx] (load-ui! skin-scale) :loaded)
  (destroy! [_] (dispose-ui!)))

(declare info-text-k-order)

(defn- sort-k-order [components]
  (sort-by (fn [[k _]] (or (index-of k info-text-k-order) 99))
           components))

(defn- remove-newlines [s]
  (let [new-s (-> s
                  (str/replace "\n\n" "\n")
                  (str/replace #"^\n" "")
                  str/trim-newline)]
    (if (= (count new-s) (count s))
      s
      (remove-newlines new-s))))

(defsystem info-text "Return info-string (for tooltips,etc.). Default nil." [_ ctx])
(defmethod info-text :default [_ ctx])

(defn ->info-text
  "Recursively generates info-text via [[info-text]]."
  [components ctx]
  (->> components
       sort-k-order
       (keep (fn [{v 1 :as component}]
               (str (try (info-text component (assoc ctx :info-text/entity* components))
                         (catch Throwable t
                           ; calling from property-editor where entity components
                           ; have a different data schema than after ->mk
                           ; and info-text might break
                           (pr-str component)))
                    (when (map? v)
                      (str "\n" (->info-text v ctx))))))
       (str/join "\n")
       remove-newlines))

(defn ->image-widget [image opts]
  (->ui-image-widget (:texture-region image) opts))

(def app-state
  "An atom referencing the current Context. Only use by ui-callbacks or for development/debugging.
  Use only with (post-runnable! & exprs) for making manual changes to the ctx."
  (atom nil))

(defn add-tooltip!
  "tooltip-text is a (fn [context] ) or a string. If it is a function will be-recalculated every show."
  [actor tooltip-text]
  (ui-add-tooltip! app-state actor tooltip-text))

(defn current-screen [{{:keys [current screens]} :context/screens}]
  [current (get screens current)])

(defsystem screen-enter "FIXME" [_ ctx])
(defmethod screen-enter :default [_ ctx])

(defsystem screen-exit  "FIXME" [_ ctx])
(defmethod screen-exit :default  [_ ctx])

(defn change-screen
  "Calls `screen-exit` on the current-screen (if there is one).
  Throws AssertionError when the context does not have a screen with screen-key.
  Calls `screen-enter` on the new screen and
  returns the context with current-screen set to new-screen."
{:arglists '([ctx screen-key])}
  [{{:keys [current screens]} :context/screens :as context}
   new-screen-key]
  (when-let [screen-v (and current
                           (current screens))]
    (screen-exit [current screen-v] context))

  (let [screen-v (new-screen-key screens)
        _ (assert screen-v (str "Cannot find screen with key: " new-screen-key))
        new-context (assoc-in context [:context/screens :current] new-screen-key)]
    (screen-enter [new-screen-key screen-v] new-context)
    new-context))

(defn- set-first-screen [context]
  (->> context
       :context/screens
       :first-screen
       (change-screen context)))

(defsystem ^:private screen-render! "FIXME" [_])

(defsystem screen-render "FIXME" [_ ctx])
(defmethod screen-render :default [_ ctx]
  ctx)

(defn create-vs
  "Creates a map for every component with map entries `[k (->mk [k v] ctx)]`."
  [components ctx]
  (reduce (fn [m [k v]]
            (assoc m k (->mk [k v] ctx)))
          {}
          components))

(defcomponent :context/screens
  {:data :some
   :let screen-ks}
  (->mk [_ ctx]
    {:screens (create-vs (zipmap screen-ks (repeat nil)) ctx)
     :first-screen (first screen-ks)})

  (destroy! [_]
    ; TODO screens not disposed https://github.com/damn/core/issues/41
    ))

; TODO not disposed anymore... screens are sub-level.... look for dispose stuff also in @ cdq! FIXME
(defcomponent :screens/stage
  {:let {:keys [stage sub-screen]}}
  (screen-enter [_ context]
    (set-input-processor! stage)
    (screen-enter sub-screen context))

  (screen-exit [_ context]
    (set-input-processor! nil)
    (screen-exit sub-screen context))

  (screen-render! [_]
    ; stage act first so user-screen calls change-screen -> is the end of frame
    ; otherwise would need render-after-stage
    ; or on change-screen the stage of the current screen would still .act
    (s-act! stage)
    (swap! app-state #(screen-render sub-screen %))
    (s-draw! stage)))

(defn ->stage [{{:keys [gui-view batch]} :context/graphics} actors]
  (let [stage (->ui-stage (:viewport gui-view) batch)]
    (run! #(s-add! stage %) actors)
    stage))

(defn stage-get [context]
  (:stage ((current-screen context) 1)))

(defn mouse-on-actor? [context]
  (s-hit (stage-get context) (gui-mouse-position context) :touchable? true))

(defn stage-add! [ctx actor]
  (-> ctx stage-get (s-add! actor))
  ctx)

(defn ->actor [{:keys [draw act]}]
  (->ui-actor (fn [] (when draw
                       (let [ctx @app-state
                             g (assoc (:context/graphics ctx) :unit-scale 1)]
                         (draw g ctx))))
              (fn [] (when act (act @app-state)))))

(defn ->text-button [text on-clicked]
  (->ui-text-button app-state text on-clicked))

; TODO check how to make toggle-able ? with hotkeys for actionbar trigger ?
(defn ->image-button
  ([image on-clicked]
   (->image-button image on-clicked {}))

  ([image on-clicked {:keys [scale]}]
   (->ui-image-button app-state (:texture-region image) scale on-clicked)))

; TODO set to preferred width/height ??? why layouting not working properly?
; use a tree?
; make example with plain data
(defn ->scroll-pane-cell [ctx rows]
  (let [table (->table {:rows rows :cell-defaults {:pad 1} :pack? true})
        scroll-pane (->scroll-pane table)]
    {:actor scroll-pane
     :width (- (gui-viewport-width ctx) 600) ;(+ (actor/width table) 200)
     :height (- (gui-viewport-height ctx) 100)})) ;(min (- (ctx/gui-viewport-height ctx) 50) (actor/height table))

(def ^:private image-file "images/moon_background.png")

(defn ->background-image [ctx]
  (->image-widget (->image ctx image-file)
                  {:fill-parent? true
                   :scaling :fill
                   :align :center}))

(defmacro ^:private with-err-str
  "Evaluates exprs in a context in which *out* is bound to a fresh
  StringWriter.  Returns the string created by any nested printing
  calls."
  {:added "1.0"}
  [& body]
  `(let [s# (new java.io.StringWriter)]
     (binding [*err* s#]
       ~@body
       (str s#))))

(defn error-window! [ctx throwable]
  (binding [*print-level* 5]
    (pretty-pst throwable 24))
  (stage-add! ctx (->window {:title "Error"
                             :rows [[(->label (binding [*print-level* 3]
                                                (with-err-str
                                                  (clojure.repl/pst throwable))))]]
                             :modal? true
                             :close-button? true
                             :close-on-escape? true
                             :center? true
                             :pack? true})))

; TODO no window movable type cursor appears here like in player idle
; inventory still working, other stuff not, because custom listener to keypresses ? use actor listeners?
; => input events handling
; hmmm interesting ... can disable @ item in cursor  / moving / etc.

(defn- show-player-modal! [ctx {:keys [title text button-text on-click]}]
  (assert (not (::modal (stage-get ctx))))
  (stage-add! ctx
              ; move into separate fns with params
              ; and othe rstuff into do
              (->window {:title title
                         :rows [[(->label text)]
                                [(->text-button button-text
                                                (fn [ctx]
                                                  (remove! (::modal (stage-get ctx)))
                                                  (on-click ctx)))]]
                         :id ::modal
                         :modal? true
                         :center-position [(/ (gui-viewport-width ctx) 2)
                                           (* (gui-viewport-height ctx) (/ 3 4))]
                         :pack? true})))

(defcomponent :tx/player-modal
  (do! [[_ params] ctx]
    (show-player-modal! ctx params)))
