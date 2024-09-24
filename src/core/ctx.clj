; TODO:
; * chekc all names _unique_ so can easily rename, e.g. check 'texture', 'cached-texture'
; => shouldnt make any regex stuff
; * maybe systems in another color and also here?
; or s/foo or c/bar
; * clone vim clojure static
; * add a different color (clj-green, or anything unused, gold) for ctx functions
; ( * and 'ctx' can also have a special color?! )
; * gdl.core or ctx.core (like clojure.core)
; or * damn.core ?
; * clojure game development extension(s)  ?
; cljgdx
; 'ctx' makes more sense ...
; * defcomponent also!! here ??
; * and build components ... then only here defcomponents
; crazy ...
; 'gdl'
(ns core.ctx
  (:require [clojure.string :as str]
            [core.utils.core :refer [index-of]])
  (:import (com.badlogic.gdx Gdx Application Files Input)
           com.badlogic.gdx.graphics.Color))

(def ^{:tag Application}               gdx-app)
(def ^{:tag Files}                     gdx-files)
(def ^{:tag Input}                     gdx-input)
(def ^{:tag com.badlogic.gdx.Graphics} gdx-graphics)

(def ^{:doc "Map of all systems as key of name-string to var."} defsystems {})

(defmacro defsystem
  "A system is a multimethod which dispatches on ffirst.
  So for a component `[k v]` it dispatches on the component-keyword `k`."
  [sys-name docstring params]
  (when (zero? (count params))
    (throw (IllegalArgumentException. "First argument needs to be component.")))
  (when-let [avar (resolve sys-name)]
    (println "WARNING: Overwriting defsystem:" avar))
  `(do
    (defmulti ~(vary-meta sys-name
                          assoc
                          :params (list 'quote params)
                          :doc (str "[[core.component/defsystem]] with params: `" params "` \n\n " docstring))
      (fn ~(symbol (str (name sys-name))) [& args#]
        (ffirst args#)))
    (alter-var-root #'defsystems assoc ~(str (ns-name *ns*) "/" sys-name) (var ~sys-name))
    (var ~sys-name)))

(defsystem ->mk "Create component value. Default returns v." [_ ctx])
(defmethod ->mk :default [[_ v] _ctx] v)

(defn create-vs
  "Creates a map for every component with map entries `[k (->mk [k v] ctx)]`."
  [components ctx]
  (reduce (fn [m [k v]]
            (assoc m k (->mk [k v] ctx)))
          {}
          components))

(defn create-into
  "For every component `[k v]`  `(core.->mk [k v] ctx)` is non-nil
  or false, assoc's at ctx k v"
  [ctx components]
  (assert (map? ctx))
  (reduce (fn [ctx [k v]]
            (if-let [v (->mk [k v] ctx)]
              (assoc ctx k v)
              ctx))
          ctx
          components))

(defsystem destroy! "Side effect destroy resources. Default do nothing." [_])
(defmethod destroy! :default [_])

(def component-attributes {})

(def ^:private warn-name-ns-mismatch? false)

(defn- k->component-ns [k] ;
  (symbol (str "components." (name (namespace k)) "." (name k))))

(defn- check-warn-ns-name-mismatch [k]
  (when (and warn-name-ns-mismatch?
             (namespace k)
             (not= (k->component-ns k) (ns-name *ns*)))
    (println "WARNING: defcomponent " k " is not matching with namespace name " (ns-name *ns*))))

(defn defcomponent*
  "Defines a component without systems methods, so only to set metadata."
  [k attr-map]
  (when (get component-attributes k)
    (println "WARNING: Overwriting defcomponent" k "attr-map"))
  (alter-var-root #'component-attributes assoc k attr-map))

(defmacro defcomponent
  "Defines a component with keyword k and optional metadata attribute-map followed by system implementations (via defmethods).

attr-map may contain `:let` binding which is let over the value part of a component `[k value]`.

Example:
```
(defsystem foo \"foo docstring.\" [_])

(defcomponent :foo/bar
  {:let {:keys [a b]}}
  (foo [_]
    (+ a b)))

(foo [:foo/bar {:a 1 :b 2}])
=> 3
```"
  [k & sys-impls]
  (check-warn-ns-name-mismatch k)
  (let [attr-map? (not (list? (first sys-impls)))
        attr-map  (if attr-map? (first sys-impls) {})
        sys-impls (if attr-map? (rest sys-impls) sys-impls)
        let-bindings (:let attr-map)
        attr-map (dissoc attr-map :let)]
    `(do
      (when ~attr-map?
        (defcomponent* ~k ~attr-map))
      #_(alter-meta! *ns* #(update % :doc str "\n* defcomponent `" ~k "`"))
      ~@(for [[sys & fn-body] sys-impls
              :let [sys-var (resolve sys)
                    sys-params (:params (meta sys-var))
                    fn-params (first fn-body)
                    fn-exprs (rest fn-body)]]
          (do
           (when-not sys-var
             (throw (IllegalArgumentException. (str sys " does not exist."))))
           (when-not (= (count sys-params) (count fn-params)) ; defmethods do not check this, that's why we check it here.
             (throw (IllegalArgumentException.
                     (str sys-var " requires " (count sys-params) " args: " sys-params "."
                          " Given " (count fn-params)  " args: " fn-params))))
           `(do
             (assert (keyword? ~k) (pr-str ~k))
             (alter-var-root #'component-attributes assoc-in [~k :params ~(name (symbol sys-var))] (quote ~fn-params))
             (when (get (methods @~sys-var) ~k)
               (println "WARNING: Overwriting defcomponent" ~k "on" ~sys-var))
             (defmethod ~sys ~k ~(symbol (str (name (symbol sys-var)) "." (name k)))
               [& params#]
               (let [~(if let-bindings let-bindings '_) (get (first params#) 1) ; get because maybe component is just [:foo] without v.
                     ~fn-params params#]
                 ~@fn-exprs)))))
      ~k)))

(defrecord Context [])

(def ^{:doc "An atom referencing the current context. Only use by ui-callbacks or for development/debugging."}
  app-state (atom nil))

(defprotocol PlaySound
  (play-sound! [_ file]
               "Sound is already loaded from file, this will perform only a lookup for the sound and play it.
  Returns ctx."))

(defprotocol TextureAsset
  (texture [_ file] "Already loaded."))

(def ^:private record-txs? false)
(def ^:private frame->txs (atom nil))

(defn- clear-recorded-txs! []
  (reset! frame->txs {}))

#_(defn summarize-txs [_ txs]
  (clojure.pprint/pprint
   (for [[txkey txs] (group-by first txs)]
     [txkey (count txs)])))

#_(defn frame->txs [_ frame-number]
  (@frame->txs frame-number))

(defcomponent :context/effect-handler
  (->mk [[_ [game-loop-mode record-transactions?]] _ctx]
    (case game-loop-mode
      :game-loop/normal (when record-transactions?
                          (clear-recorded-txs!)
                          (.bindRoot #'record-txs? true))
      :game-loop/replay (do
                         (assert record-txs?)
                         (.bindRoot #'record-txs? false)
                         ;(println "Initial entity txs:")
                         ;(ctx/summarize-txs ctx (ctx/frame->txs ctx 0))
                         ))
    nil))

(defn- add-tx-to-frame [frame->txs frame-num tx]
  (update frame->txs frame-num (fn [txs-at-frame]
                                 (if txs-at-frame
                                   (conj txs-at-frame tx)
                                   [tx]))))

(comment
 (= (-> {}
        (add-tx-to-frame 1 [:foo1 :bar1])
        (add-tx-to-frame 1 [:foo2 :bar2]))
    {1 [[:foo1 :bar1] [:foo2 :bar2]]})
 )

(def ^:private ^:dbg-flag debug-print-txs? false)

(defn- debug-print-tx [tx]
  (pr-str (mapv #(cond
                  (instance? clojure.lang.Atom %) (str "<entity-atom{uid=" (:entity/uid @%) "}>")
                  :else %)
                tx)))

#_(defn- tx-happened! [tx ctx]
  (when (and
         (not (fn? tx))
         (not= :tx/cursor (first tx)))
    (let [logic-frame (time/logic-frame ctx)] ; only if debug or record deref this?
      (when debug-print-txs?
        (println logic-frame "." (debug-print-tx tx)))
      (when (and record-txs?
                 (not= (first tx) :tx/effect))
        (swap! frame->txs add-tx-to-frame logic-frame tx)))))

; 1. return new ctx if we change something in the ctx or have side effect -> will be recorded
; when returning a 'map?'

; 2. return seq of txs -> those txs will be done recursively
; 2.1 also seq of fns wih [ctx] param can be passed.

; 3. return nil in case of doing nothing -> will just continue with existing ctx.

; do NOT do a effect/do inside a effect/do! because then we have to return a context
; and that means that transaction will be recorded and done double with all the sub-transactions
; in the replay mode
; we only want to record actual side effects, not transactions returning other lower level transactions
(defsystem do! "FIXME" [_ ctx])

(declare effect!)

(defn- handle-tx! [ctx tx]
  (let [result (if (fn? tx)
                 (tx ctx)
                 (do! tx ctx))]
    (if (map? result) ; new context
      (do
       #_(tx-happened! tx ctx)
       result)
      (effect! ctx result))))

(defn effect! [ctx txs]
  (reduce (fn [ctx tx]
            (if (nil? tx)
              ctx
              (try
               (handle-tx! ctx tx)
               (catch Throwable t
                 (throw (ex-info "Error with transaction"
                                 {:tx tx #_(debug-print-tx tx)}
                                 t))))))
          ctx
          txs))

(defprotocol RenderWorldView
  (render-world-view [_ render-fn] "render-fn is a function of param 'g', graphics context."))

(defprotocol MouseOverEntity
  (mouseover-entity* [_]))

(defprotocol Player
  (player-entity [_])
  (player-entity* [_]))

(defsystem applicable?
  "An effect will only be done (with do!) if this function returns truthy.
Required system for every effect, no default."
  [_ ctx])

(defsystem useful?
  "Used for NPC AI.
Called only if applicable? is truthy.
For example use for healing effect is only useful if hitpoints is < max.
Default method returns true."
  [_ ctx])
(defmethod useful? :default [_ ctx] true)

(defsystem info-text "Return info-string (for tooltips,etc.). Default nil." [_ ctx])
(defmethod info-text :default [_ ctx])

(def ^:private k-order
  [:property/pretty-name
   :skill/action-time-modifier-key
   :skill/action-time
   :skill/cooldown
   :skill/cost
   :skill/effects
   :creature/species
   :creature/level
   :entity/stats
   :entity/delete-after-duration
   :projectile/piercing?
   :entity/projectile-collision
   :maxrange
   :entity-effects])

(defn- sort-by-order [components]
  (sort-by (fn [[k _]] (or (index-of k k-order) 99))
           components))

(defn- remove-newlines [s]
  (let [new-s (-> s
                  (str/replace "\n\n" "\n")
                  (str/replace #"^\n" "")
                  str/trim-newline)]
    (if (= (count new-s) (count s))
      s
      (remove-newlines new-s))))

(defn ->info-text
  "Recursively generates info-text via [[core.info-text]]."
  [components ctx]
  (->> components
       sort-by-order
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

(defprotocol Entities
  (all-entities [_])
  (get-entity [_ uid]))

(defn def-attributes [& attributes-data]
  {:pre [(even? (count attributes-data))]}
  (doseq [[k data] (partition 2 attributes-data)]
    (defcomponent* k {:data data})))

(defcomponent :property/id {:data [:qualified-keyword]})

(defn def-type [k {:keys [schema overview]}]
  (defcomponent k
    {:data [:map (conj schema :property/id)]
     :overview overview}))

(defprotocol Property
  (build-property [_ id]))

(defn ->color
  ([r g b]
   (->color r g b 1))
  ([r g b a]
   (Color. (float r) (float g) (float b) (float a))))

(defrecord Graphics [batch
                     shape-drawer
                     gui-view
                     world-view
                     default-font
                     unit-scale
                     cursors])

(defprotocol WorldView
  (pixels->world-units [_ pixels])
  (world-unit-scale [_]))

(defprotocol PShapeDrawer
  (draw-ellipse [_ position radius-x radius-y color])
  (draw-filled-ellipse [_ position radius-x radius-y color])
  (draw-circle [_ position radius color])
  (draw-filled-circle [_ position radius color])
  (draw-arc [_ center-position radius start-angle degree color])
  (draw-sector [_ center-position radius start-angle degree color])
  (draw-rectangle [_ x y w h color])
  (draw-filled-rectangle [_ x y w h color])
  (draw-line [_ start-position end-position color])
  (draw-grid [drawer leftx bottomy gridw gridh cellw cellh color])
  (with-shape-line-width [_ width draw-fn]))

(defprotocol TextDrawer
  (draw-text [_ {:keys [x y text font h-align up? scale]}]
             "font, h-align, up? and scale are optional.
             h-align one of: :center, :left, :right. Default :center.
             up? renders the font over y, otherwise under.
             scale will multiply the drawn text size with the scale."))

(defprotocol ImageDraw
  (draw-image [_ image position])
  (draw-centered-image [_ image position])
  (draw-rotated-centered-image [_ image rotation position]))

(defprotocol Views
  (gui-mouse-position    [_])
  (world-mouse-position  [_])
  (gui-viewport-width    [_])
  (gui-viewport-height   [_])
  (world-camera          [_])
  (world-viewport-width  [_])
  (world-viewport-height [_]))
