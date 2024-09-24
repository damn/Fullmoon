
;; the name of the project/main ns => google it and grep and no results

; TODO:
; !! * chekc all names _unique_ so can easily rename, e.g. check 'texture', 'cached-texture'
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
  (:require [clojure.string :as str])
  (:import (com.badlogic.gdx Gdx Application Files Input)
           com.badlogic.gdx.audio.Sound
           com.badlogic.gdx.assets.AssetManager
           com.badlogic.gdx.files.FileHandle
           (com.badlogic.gdx.graphics Color Texture)
           (com.badlogic.gdx.graphics.g2d TextureRegion Batch)
           com.badlogic.gdx.utils.Disposable))

(defn remove-one [coll item]
  (let [[n m] (split-with (partial not= item) coll)]
    (concat n (rest m))))

(defn mapvals [f m]
  (into {} (for [[k v] m]
             [k (f v)])))

(defn k->pretty-name [k]
  (str/capitalize (name k)))

(defn find-first ; from clojure.contrib.seq-utils (discontinued in 1.3)
  "Returns the first item of coll for which (pred item) returns logical true.
  Consumes sequences up to the first match, will consume the entire sequence
  and return nil if no match is found."
  [pred coll]
  (first (filter pred coll)))

(defn ->tile [position]
  (mapv int position))

(defn tile->middle [position]
  (mapv (partial + 0.5) position))

(let [obj (Object.)]
  (defn safe-get [m k]
    (let [result (get m k obj)]
      (if (= result obj)
        (throw (IllegalArgumentException. (str "Cannot find " (pr-str k))))
        result))))

(defn safe-merge [m1 m2]
  {:pre [(not-any? #(contains? m1 %) (keys m2))]}
  (merge m1 m2))

; libgdx fn is available:
; (MathUtils/isEqual 1 (length v))
(defn- approx-numbers [a b epsilon]
  (<=
    (Math/abs (float (- a b)))
    epsilon))

(defn- round-n-decimals [^double x n]
  (let [z (Math/pow 10 n)]
    (float
      (/
        (Math/round (float (* x z)))
        z))))

(defn readable-number [^double x]
  {:pre [(number? x)]} ; do not assert (>= x 0) beacuse when using floats x may become -0.000...000something
  (if (or
        (> x 5)
        (approx-numbers x (int x) 0.001)) ; for "2.0" show "2" -> simpler
    (int x)
    (round-n-decimals x 2)))

(defn index-of [k ^clojure.lang.PersistentVector v]
  (let [idx (.indexOf v k)]
    (if (= -1 idx)
      nil
      idx)))

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


(defn- ->asset-manager ^AssetManager []
  (proxy [AssetManager clojure.lang.ILookup] []
    (valAt [file]
      (.get ^AssetManager this ^String file))))

(defn- recursively-search [folder extensions]
  (loop [[^FileHandle file & remaining] (.list (.internal gdx-files folder))
         result []]
    (cond (nil? file)
          result

          (.isDirectory file)
          (recur (concat remaining (.list file)) result)

          (extensions (.extension file))
          (recur remaining (conj result (.path file)))

          :else
          (recur remaining result))))

(defn- load-assets! [manager files ^Class class log?]
  (doseq [file files]
    (when log?
      (println "load-assets" (str "[" (.getSimpleName class) "] - [" file "]")))
    (.load ^AssetManager manager ^String file class)))

(defn- asset-files [folder file-extensions]
  (map #(str/replace-first % folder "")
       (recursively-search folder file-extensions)))

(def assets :context/assets)

(defcomponent assets
  {:data :some
   :let {:keys [folder
                sound-file-extensions
                image-file-extensions
                log?]}}
  (->mk [_ _ctx]
    (let [manager (->asset-manager)
          sound-files   (asset-files folder sound-file-extensions)
          texture-files (asset-files folder image-file-extensions)]
      (load-assets! manager sound-files   Sound   log?)
      (load-assets! manager texture-files Texture log?)
      (.finishLoading manager)
      {:manager manager
       :sound-files sound-files
       :texture-files texture-files})))

(defn- get-asset [ctx file]
  (get (:manager (assets ctx)) file))

(defn play-sound!
  "Sound is already loaded from file, this will perform only a lookup for the sound and play it.
Returns ctx."
  [ctx file]
  (.play ^Sound (get-asset ctx file))
  ctx)

(defn texture
  "Is already cached and loaded."
  [ctx file]
  (get-asset ctx file))

(defcomponent :tx/sound
  {:data :sound}
  (do! [[_ file] ctx]
    (play-sound! ctx file)))

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

(def-attributes
  :tag [:enum [:dev :prod]]
  :configs :some)

(defcomponent :context/config
  {:data [:map [:tag :configs]]
   :let {:keys [tag configs]}}
  (->mk [_ _ctx]
    (get configs tag)))

(defn dispose [obj] (Disposable/.dispose obj))

(defprotocol ActiveEntities
  (active-entities [_]))

(def color-black Color/BLACK)
(def color-white Color/WHITE)

(defsystem screen-enter "FIXME" [_ ctx])
(defmethod screen-enter :default [_ ctx])

(defsystem screen-exit  "FIXME" [_ ctx])
(defmethod screen-exit :default  [_ ctx])

(defsystem screen-render! "FIXME" [_ app-state])

(defsystem screen-render "FIXME" [_ ctx])
(defmethod screen-render :default [_ ctx]
  ctx)

(com.badlogic.gdx.graphics.Colors/put
 "ITEM_GOLD"
 (com.badlogic.gdx.graphics.Color. (float 0.84)
                                   (float 0.8)
                                   (float 0.52)
                                   (float 1)))

(defcomponent :property/pretty-name
  {:data :string
   :let value}
  (info-text [_ _ctx]
    (str "[ITEM_GOLD]"value"[]")))

(defprotocol RayCaster
  (ray-blocked? [ctx start target])
  (path-blocked? [ctx start target path-w] "path-w in tiles. casts two rays."))

(defn ->texture-region
  ([^Texture tex]
   (TextureRegion. tex))

  ([^TextureRegion texture-region [x y w h]]
   (TextureRegion. texture-region (int x) (int y) (int w) (int h))))

(defrecord Image [texture-region
                  pixel-dimensions
                  world-unit-dimensions
                  color]) ; optional

(defn- unit-dimensions [image unit-scale]
  (if (= unit-scale 1)
    (:pixel-dimensions image)
    (:world-unit-dimensions image)))

(defn- scale-dimensions [dimensions scale]
  (mapv (comp float (partial * scale)) dimensions))

(defn- texture-region-dimensions [^TextureRegion texture-region]
  [(.getRegionWidth  texture-region)
   (.getRegionHeight texture-region)])

(defn- assoc-dimensions
  "scale can be a number for multiplying the texture-region-dimensions or [w h]."
  [{:keys [texture-region] :as image} g scale]
  {:pre [(or (number? scale)
             (and (vector? scale)
                  (number? (scale 0))
                  (number? (scale 1))))]}
  (let [pixel-dimensions (if (number? scale)
                           (scale-dimensions (texture-region-dimensions texture-region) scale)
                           scale)]
    (assoc image
           :pixel-dimensions pixel-dimensions
           :world-unit-dimensions (scale-dimensions pixel-dimensions (world-unit-scale g)))))

; TODO [x y] is center or left-bottom ?
; why rotation origin calculations ?!
(defn- draw-texture-region [^Batch batch texture-region [x y] [w h] rotation color]
  (if color (.setColor batch color)) ; TODO move out, simplify ....
  (.draw batch
         texture-region
         x
         y
         (/ (float w) 2) ; rotation origin
         (/ (float h) 2)
         w ; width height
         h
         1 ; scaling factor
         1
         rotation)
  (if color (.setColor batch Color/WHITE)))

(extend-type core.ctx.Graphics
  core.ctx/ImageDraw
  (draw-image [{:keys [batch unit-scale]}
               {:keys [texture-region color] :as image}
               position]
    (draw-texture-region batch
                         texture-region
                         position
                         (unit-dimensions image unit-scale)
                         0 ; rotation
                         color))

  (draw-rotated-centered-image [{:keys [batch unit-scale]}
                                {:keys [texture-region color] :as image}
                                rotation
                                [x y]]
    (let [[w h] (unit-dimensions image unit-scale)]
      (draw-texture-region batch
                           texture-region
                           [(- (float x) (/ (float w) 2))
                            (- (float y) (/ (float h) 2))]
                           [w h]
                           rotation
                           color)))

  (draw-centered-image [this image position]
    (draw-rotated-centered-image this image 0 position)))

(defn- ->image* [g texture-region]
  (-> {:texture-region texture-region}
      (assoc-dimensions g 1)
      map->Image))

(defn ->image [{g :context/graphics :as ctx} file]
  (->image* g (->texture-region (texture ctx file)))) ; TODO why doesnt texture work?

(defn sub-image [{g :context/graphics} {:keys [texture-region]} bounds]
  (->image* g (->texture-region texture-region bounds)))

(defn sprite-sheet [ctx file tilew tileh]
  {:image (->image ctx file)
   :tilew tilew
   :tileh tileh})

(defn sprite
  "x,y index starting top-left"
  [ctx {:keys [image tilew tileh]} [x y]]
  (sub-image ctx image [(* x tilew) (* y tileh) tilew tileh]))

(defn ^:no-doc edn->image [{:keys [file sub-image-bounds]} ctx]
  (if sub-image-bounds
    (let [[sprite-x sprite-y] (take 2 sub-image-bounds)
          [tilew tileh]       (drop 2 sub-image-bounds)]
      (sprite ctx
              (sprite-sheet ctx file tilew tileh)
              [(int (/ sprite-x tilew))
               (int (/ sprite-y tileh))]))
    (->image ctx file)))
