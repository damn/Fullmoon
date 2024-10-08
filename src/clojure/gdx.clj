(ns clojure.gdx
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [clojure.edn :as edn]
            [clojure.math :as math]
            [clojure.pprint :refer [pprint]]
            [clojure.gdx.tiled :as t]
            [clj-commons.pretty.repl :refer [pretty-pst]]
            [data.grid2d :as g]
            [malli.core :as m]
            [malli.error :as me]
            [malli.generator :as mg])
  (:import (com.badlogic.gdx Gdx ApplicationAdapter)
           (com.badlogic.gdx.assets AssetManager)
           (com.badlogic.gdx.audio Sound)
           (com.badlogic.gdx.backends.lwjgl3 Lwjgl3Application Lwjgl3ApplicationConfiguration)
           (com.badlogic.gdx.files FileHandle)
           (com.badlogic.gdx.graphics Color Colors Texture Texture$TextureFilter OrthographicCamera Camera Pixmap Pixmap$Format)
           (com.badlogic.gdx.graphics.g2d SpriteBatch Batch BitmapFont TextureRegion)
           (com.badlogic.gdx.graphics.g2d.freetype FreeTypeFontGenerator FreeTypeFontGenerator$FreeTypeFontParameter)
           (com.badlogic.gdx.math MathUtils Vector2 Vector3 Circle Rectangle Intersector)
           (com.badlogic.gdx.utils Align Scaling Disposable SharedLibraryLoader ScreenUtils)
           (com.badlogic.gdx.utils.viewport Viewport FitViewport)
           (com.badlogic.gdx.scenes.scene2d Actor Touchable Group Stage)
           (com.badlogic.gdx.scenes.scene2d.ui Widget Image Label Button Table Cell WidgetGroup Stack ButtonGroup HorizontalGroup VerticalGroup Window Tree$Node)
           (com.badlogic.gdx.scenes.scene2d.utils ClickListener ChangeListener TextureRegionDrawable Drawable)
           (com.kotcrab.vis.ui VisUI VisUI$SkinScale)
           (com.kotcrab.vis.ui.widget Tooltip VisTextButton VisCheckBox VisSelectBox VisImage VisImageButton VisTextField VisWindow VisTable VisLabel VisSplitPane VisScrollPane Separator VisTree)
           (space.earlygrey.shapedrawer ShapeDrawer)
           (gdl RayCaster)))

(def defsystems "Map of all systems as key of name-string to var." {})
(def component-attributes {})

(defmacro defsystem
  "A system is a multimethod which takes a component `[k v]` and dispatches on k."
  [sys-name docstring params]
  (when (zero? (count params))
    (throw (IllegalArgumentException. "First argument needs to be component.")))
  (when-let [avar (resolve sys-name)]
    (println "WARNING: Overwriting defsystem:" avar))
  `(do
    (defmulti ~(vary-meta sys-name assoc :params (list 'quote params))
      ~(str "[[defsystem]] with params: `" params "` \n\n " docstring)
      (fn ~(symbol (str (name sys-name))) [[k# _#] & args#] k#))
    (alter-var-root #'defsystems assoc ~(str (ns-name *ns*) "/" sys-name) (var ~sys-name))
    (var ~sys-name)))

(def ^:private warn-name-ns-mismatch? false)

(defn- k->component-ns [k] ;
  (symbol (str "components." (name (namespace k)) "." (name k))))

(defn- check-warn-ns-name-mismatch [k]
  (when (and warn-name-ns-mismatch?
             (namespace k)
             (not= (k->component-ns k) (ns-name *ns*)))
    (println "WARNING: defc " k " is not matching with namespace name " (ns-name *ns*))))

(defn defc*
  "Defines a component without systems methods, so only to set metadata."
  [k attr-map]
  (when (get component-attributes k)
    (println "WARNING: Overwriting defc" k "attr-map"))
  (alter-var-root #'component-attributes assoc k attr-map))

(defmacro defc
  "Defines a component with keyword k and optional metadata attribute-map followed by system implementations (via defmethods).

attr-map may contain `:let` binding which is let over the value part of a component `[k value]`.

Example:
```clojure
(defsystem foo \"foo docstring.\" [_])

(defc :foo/bar
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
        (defc* ~k ~attr-map))
      #_(alter-meta! *ns* #(update % :doc str "\n* defc `" ~k "`"))
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
               (println "WARNING: Overwriting defc" ~k "on" ~sys-var))
             (defmethod ~sys ~k ~(symbol (str (name (symbol sys-var)) "." (name k)))
               [& params#]
               (let [~(if let-bindings let-bindings '_) (get (first params#) 1) ; get because maybe component is just [:foo] without v.
                     ~fn-params params#]
                 ~@fn-exprs)))))
      ~k)))

(defsystem ->mk "Create component value. Default returns v." [_])
(defmethod ->mk :default [[_ v]] v)

(defsystem ^:private destroy! "Side effect destroy resources. Default do nothing." [_])
(defmethod destroy! :default [_])

;;;;

(def ^:private ^:dbg-flag debug-print-txs? false)

(defn- debug-print-tx [tx]
  (pr-str (mapv #(cond
                  (instance? clojure.lang.Atom %) (str "<entity-atom{uid=" (:entity/uid @%) "}>")
                  :else %)
                tx)))

#_(defn- tx-happened! [tx]
    (when (and
           (not (fn? tx))
           (not= :tx/cursor (first tx)))
      (when debug-print-txs?
        (println logic-frame "." (debug-print-tx tx)))))

(defsystem do!
  "Return nil or new coll/seq of txs to be done recursively."
  [_])

(defn effect!
  "An effect is defined as a sequence of txs(transactions).

A tx is either a (fn []) with no args or a component which implements the do! system.

All txs are being executed in sequence, any nil are skipped.

If the result of a tx is non-nil, we assume a new sequence of txs and effect! calls itself recursively.

On any exception we get a stacktrace with all tx's values and names shown."
  [effect]
  (doseq [tx effect
          :when tx]
    (try (when-let [result (if (fn? tx)
                             (tx)
                             (do! tx))]
           (effect! result))
         (catch Throwable t
           (throw (ex-info "Error with transaction"
                           {:tx tx #_(debug-print-tx tx)}
                           t))))))

;;;;

(def ^:private val-max-schema
  (m/schema [:and
             [:vector {:min 2 :max 2} [:int {:min 0}]]
             [:fn {:error/fn (fn [{[^int v ^int mx] :value} _]
                               (when (< mx v)
                                 (format "Expected max (%d) to be smaller than val (%d)" v mx)))}
              (fn [[^int a ^int b]] (<= a b))]]))

(defn val-max-ratio
  "If mx and v is 0, returns 0, otherwise (/ v mx)"
  [[^int v ^int mx]]
  {:pre [(m/validate val-max-schema [v mx])]}
  (if (and (zero? v) (zero? mx))
    0
    (/ v mx)))

#_(defn lower-than-max? [[^int v ^int mx]]
  {:pre [(m/validate val-max-schema [v mx])]}
  (< v mx))

#_(defn set-to-max [[v mx]]
  {:pre [(m/validate val-max-schema [v mx])]}
  [mx mx])


;;;;

(defn- +? [n]
  (case (math/signum n)
    0.0 ""
    1.0 "+"
    -1.0 ""))

(defsystem op-value-text "FIXME" [_])

(defn op-info-text [{value 1 :as operation}]
  (str (+? value) (op-value-text operation)))

(defsystem op-apply "FIXME" [_ base-value])
(defsystem op-order "FIXME" [_])

(defc :op/inc
  {:data :number
   :let value}
  (op-value-text [_] (str value))
  (op-apply [_ base-value] (+ base-value value))
  (op-order [_] 0))

(defc :op/mult
  {:data :number
   :let value}
  (op-value-text [_] (str (int (* 100 value)) "%"))
  (op-apply [_ base-value] (* base-value (inc value)))
  (op-order [_] 1))

(defn- ->pos-int [v]
  (-> v int (max 0)))

(defn- val-max-op-k->parts [op-k]
  (let [[val-or-max inc-or-mult] (mapv keyword (str/split (name op-k) #"-"))]
    [val-or-max (keyword "op" (name inc-or-mult))]))

(comment
 (= (val-max-op-k->parts :op/val-inc) [:val :op/inc])
 )

(defc :op/val-max
  (op-value-text [[op-k value]]
    (let [[val-or-max op-k] (val-max-op-k->parts op-k)]
      (str (op-value-text [op-k value]) " " (case val-or-max
                                              :val "Minimum"
                                              :max "Maximum"))))


  (op-apply [[operation-k value] val-max]
    (assert (m/validate val-max-schema val-max) (pr-str val-max))
    (let [[val-or-max op-k] (val-max-op-k->parts operation-k)
          f #(op-apply [op-k value] %)
          [v mx] (update val-max (case val-or-max :val 0 :max 1) f)
          v  (->pos-int v)
          mx (->pos-int mx)
          vmx (case val-or-max
                :val [v (max v mx)]
                :max [(min v mx) mx])]
      (assert (m/validate val-max-schema vmx))
      vmx))

  (op-order [[op-k value]]
    (let [[_ op-k] (val-max-op-k->parts op-k)]
      (op-order [op-k value]))))

(defc :op/val-inc {:data :int})
(derive       :op/val-inc :op/val-max)

(defc :op/val-mult {:data :number})
(derive       :op/val-mult :op/val-max)

(defc :op/max-inc {:data :int})
(derive       :op/max-inc :op/val-max)

(defc :op/max-mult {:data :number})
(derive       :op/max-mult :op/val-max)

(comment
 (and
  (= (op-apply [:op/val-inc 30]    [5 10]) [35 35])
  (= (op-apply [:op/max-mult -0.5] [5 10]) [5 5])
  (= (op-apply [:op/val-mult 2]    [5 10]) [15 15])
  (= (op-apply [:op/val-mult 1.3]  [5 10]) [11 11])
  (= (op-apply [:op/max-mult -0.8] [5 10]) [1 1])
  (= (op-apply [:op/max-mult -0.9] [5 10]) [0 0]))
 )

;;;;

(defsystem ^:private ->value "..." [_])

(defn def-attributes [& attributes-data]
  {:pre [(even? (count attributes-data))]}
  (doseq [[k data] (partition 2 attributes-data)]
    (defc* k {:data data})))

(defn def-property-type [k {:keys [schema overview]}]
  (defc k
    {:data [:map (conj schema :property/id)]
     :overview overview}))

(defn safe-get [m k]
  (let [result (get m k ::not-found)]
    (if (= result ::not-found)
      (throw (IllegalArgumentException. (str "Cannot find " (pr-str k))))
      result)))

(defn- data-component [k]
  (try (let [data (:data (safe-get component-attributes k))]
         (if (vector? data)
           [(first data) (->value data)]
           [data (safe-get component-attributes data)]))
       (catch Throwable t
         (throw (ex-info "" {:k k} t)))))

;;;;

(comment

 (defn- raw-properties-of-type [ptype]
   (->> (vals properties-db)
        (filter #(= ptype (->type %)))))

 (defn- migrate [ptype prop-fn]
   (let [cnt (atom 0)]
     (time
      (doseq [prop (map prop-fn (raw-properties-of-type ptype))]
        (println (swap! cnt inc) (:property/id prop) ", " (:property/pretty-name prop))
        (update! prop)))
     ; omg every update! calls async-write-to-file ...
     ; actually if its async why does it take so long ?
     (async-write-to-file! @app-state)
     nil))

 (migrate :properties/creatures
          (fn [prop]
            (-> prop
                (assoc :entity/tags ""))))

 )

(defn- property-type->id-namespace [property-type]
  (keyword (name property-type)))

(defn- attribute-schema
  "Can define keys as just keywords or with properties like [:foo {:optional true}]."
  [ks]
  (for [k ks
        :let [k? (keyword? k)
              properties (if k? nil (k 1))
              k (if k? k (k 0))]]
    (do
     (assert (keyword? k))
     (assert (or (nil? properties) (map? properties)) (pr-str ks))
     [k properties (:schema ((data-component k) 1))])))

(defn- map-schema [ks]
  (apply vector :map {:closed true} (attribute-schema ks)))

(defn- namespaced-ks [ns-name-k]
  (filter #(= (name ns-name-k) (namespace %))
          (keys component-attributes)))

;;;; Component Data Schemas

(defc :some    {:schema :some})
(defc :boolean {:schema :boolean})
(defc :string  {:schema :string})
(defc :number  {:schema number?})
(defc :nat-int {:schema nat-int?})
(defc :int     {:schema int?})
(defc :pos     {:schema pos?})
(defc :pos-int {:schema pos-int?})
(defc :sound   {:schema :string})
(defc :val-max {:schema (m/form val-max-schema)})
(defc :image   {:schema [:map {:closed true}
                         [:file :string]
                         [:sub-image-bounds {:optional true} [:vector {:size 4} nat-int?]]]})
(defc :data/animation {:schema [:map {:closed true}
                                [:frames :some]
                                [:frame-duration pos?]
                                [:looping? :boolean]]})

(defc :enum
  (->value [[_ items]]
    {:schema (apply vector :enum items)}))

(defc :qualified-keyword
  (->value [schema]
    {:schema schema}))

(defc :map
  (->value [[_ ks]]
    {:schema (map-schema ks)}))

(defc :map-optional
  (->value [[_ ks]]
    {:schema (map-schema (map (fn [k] [k {:optional true}]) ks))}))

(defc :components-ns
  (->value [[_ ns-name-k]]
    (->value [:map-optional (namespaced-ks ns-name-k)])))

(defc :one-to-many
  (->value [[_ property-type]]
    {:schema [:set [:qualified-keyword {:namespace (property-type->id-namespace property-type)}]]}))

(defc :one-to-one
  (->value [[_ property-type]]
    {:schema [:qualified-keyword {:namespace (property-type->id-namespace property-type)}]}))

;;;;

(defmulti edn->value (fn [data v] (if data (data 0))))
(defmethod edn->value :default [_data v]
  v)

(defn- ns-k->property-type [ns-k]
  (keyword "properties" (name ns-k)))

(defn- ->type [{:keys [property/id]}]
  (keyword "properties" (namespace id)))

(defn prop->image [{:keys [entity/image entity/animation]}]
  (or image
      (first (:frames animation))))

(defn- types []
  (filter #(= "properties" (namespace %)) (keys component-attributes)))

(defn- overview [property-type]
  (:overview (get component-attributes property-type)))

(defn- ->schema [property]
  (-> property
      ->type
      data-component
      (get 1)
      :schema
      m/schema))

(defn- validate [property]
  (let [schema (->schema property)
        valid? (try (m/validate schema property)
                    (catch Throwable t
                      (throw (ex-info "m/validate fail" {:property property} t))))]
    (when-not valid?
      (throw (ex-info (str (me/humanize (m/explain schema property)))
                      {:property property
                       :schema (m/form schema)})))))

(defc :property/id {:data [:qualified-keyword]})

(defn bind-root [avar value] (alter-var-root avar (constantly value)))

(declare ^:private properties-db
         ^:private properties-edn-file)

(defn load-properties-db! [file]
  (let [properties (-> file slurp edn/read-string)]
    (assert (apply distinct? (map :property/id properties)))
    (run! validate properties)
    (bind-root #'properties-db (zipmap (map :property/id properties) properties))
    (bind-root #'properties-edn-file properties-edn-file)))

(defn- async-pprint-spit! [properties]
  (.start
   (Thread.
    (fn []
      (binding [*print-level* nil]
        (->> properties
             pprint
             with-out-str
             (spit properties-edn-file)))))))

(defn- recur-sort-map [m]
  (into (sorted-map)
        (zipmap (keys m)
                (map #(if (map? %)
                        (recur-sort-map %)
                        %)
                     (vals m)))))

(defn- async-write-to-file! []
  (->> properties-db
       vals
       (sort-by ->type)
       (map recur-sort-map)
       doall
       async-pprint-spit!))

(def ^:private undefined-data-ks (atom #{}))

(comment
 #{:frames
   :looping?
   :frame-duration
   :file
   :sub-image-bounds})

; reduce-kv?
(defn- apply-kvs
  "Calls for every key in map (f k v) to calculate new value at k."
  [m f]
  (reduce (fn [m k]
            (assoc m k (f k (get m k)))) ; using assoc because non-destructive for records
          m
          (keys m)))

(defn- build [property]
  (apply-kvs property
             (fn [k v]
               (edn->value (try (data-component k)
                                (catch Throwable _t
                                  (swap! undefined-data-ks conj k)))
                           (if (map? v)
                             (build v)
                             v)))))

(defn build-property [id]
  (build (safe-get properties-db id)))

(defn all-properties [type]
  (->> (vals properties-db)
       (filter #(= type (->type %)))
       (map build)))

(defn- update! [{:keys [property/id] :as property}]
  {:pre [(contains? property :property/id)
         (contains? properties-db id)]}
  (validate property)
  (alter-var-root #'properties-db assoc id property)
  (async-write-to-file!))

(defn- delete! [property-id]
  {:pre [(contains? properties-db property-id)]}
  (alter-var-root #'properties-db dissoc property-id)
  (async-write-to-file!))

;;;;

(declare info-text-k-order)

(defn index-of [k ^clojure.lang.PersistentVector v]
  (let [idx (.indexOf v k)]
    (if (= -1 idx)
      nil
      idx)))

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

(defsystem info-text "Return info-string (for tooltips,etc.). Default nil." [_])
(defmethod info-text :default [_])

(declare ^:dynamic *info-text-entity*)

(defn ->info-text
  "Recursively generates info-text via [[info-text]]."
  [components]
  (->> components
       sort-k-order
       (keep (fn [{v 1 :as component}]
               (str (try (binding [*info-text-entity* components]
                           (info-text component))
                         (catch Throwable t
                           ; calling from property-editor where entity components
                           ; have a different data schema than after ->mk
                           ; and info-text might break
                           (pr-str component)))
                    (when (map? v)
                      (str "\n" (->info-text v))))))
       (str/join "\n")
       remove-newlines))

(defn equal?
  "Returns true if a is nearly equal to b."
  [a b]
  (MathUtils/isEqual a b))

(defn- degree->radians [degree]
  (* (float degree) MathUtils/degreesToRadians))

(defn clamp [value min max]
  (MathUtils/clamp (float value) (float min) (float max)))

; TODO not important badlogic, using clojure vectors
; could extend some protocol by clojure vectors and just require the protocol
; also call vector2 v2/add ? v2/scale ?

(defn- ^Vector2 ->v [[x y]]
  (Vector2. x y))

(defn- ->p [^Vector2 v]
  [(.x ^Vector2 v)
   (.y ^Vector2 v)])

(defn v-scale     [v n]    (->p (.scl ^Vector2 (->v v) (float n)))) ; TODO just (mapv (partial * 2) v)
(defn v-normalise [v]      (->p (.nor ^Vector2 (->v v))))
(defn v-add       [v1 v2]  (->p (.add ^Vector2 (->v v1) ^Vector2 (->v v2))))
(defn v-length    [v]      (.len ^Vector2 (->v v)))
(defn v-distance  [v1 v2]  (.dst ^Vector2 (->v v1) ^Vector2 (->v v2)))

(defn v-normalised? [v] (equal? 1 (v-length v)))

(defn v-get-normal-vectors [[x y]]
  [[(- (float y))         x]
   [          y (- (float x))]])

(defn v-direction [[sx sy] [tx ty]]
  (v-normalise [(- (float tx) (float sx))
                (- (float ty) (float sy))]))

(defn v-get-angle-from-vector
  "converts theta of Vector2 to angle from top (top is 0 degree, moving left is 90 degree etc.), ->counterclockwise"
  [v]
  (.angleDeg (->v v) (Vector2. 0 1)))

(comment

 (pprint
  (for [v [[0 1]
           [1 1]
           [1 0]
           [1 -1]
           [0 -1]
           [-1 -1]
           [-1 0]
           [-1 1]]]
    [v
     (.angleDeg (->v v) (Vector2. 0 1))
     (get-angle-from-vector (->v v))]))

 )

(defn- ->circle [[x y] radius]
  (Circle. x y radius))

(defn- ->rectangle [[x y] width height]
  (Rectangle. x y width height))

(defn- rect-contains? [^Rectangle rectangle [x y]]
  (.contains rectangle x y))

(defmulti ^:private overlaps? (fn [a b] [(class a) (class b)]))

(defmethod overlaps? [Circle Circle]
  [^Circle a ^Circle b]
  (Intersector/overlaps a b))

(defmethod overlaps? [Rectangle Rectangle]
  [^Rectangle a ^Rectangle b]
  (Intersector/overlaps a b))

(defmethod overlaps? [Rectangle Circle]
  [^Rectangle rect ^Circle circle]
  (Intersector/overlaps circle rect))

(defmethod overlaps? [Circle Rectangle]
  [^Circle circle ^Rectangle rect]
  (Intersector/overlaps circle rect))

(defn- rectangle? [{[x y] :left-bottom :keys [width height]}]
  (and x y width height))

(defn- circle? [{[x y] :position :keys [radius]}]
  (and x y radius))

(defn- m->shape [m]
  (cond
   (rectangle? m) (let [{:keys [left-bottom width height]} m]
                    (->rectangle left-bottom width height))

   (circle? m) (let [{:keys [position radius]} m]
                 (->circle position radius))

   :else (throw (Error. (str m)))))

(defn shape-collides? [a b]
  (overlaps? (m->shape a) (m->shape b)))

(defn point-in-rect? [point rectangle]
  (rect-contains? (m->shape rectangle) point))

(defn circle->outer-rectangle [{[x y] :position :keys [radius] :as circle}]
  {:pre [(circle? circle)]}
  (let [radius (float radius)
        size (* radius 2)]
    {:left-bottom [(- (float x) radius)
                   (- (float y) radius)]
     :width  size
     :height size}))

(defprotocol PFastRayCaster
  (fast-ray-blocked? [_ start target]))

; boolean array used because 10x faster than access to clojure grid data structure

; this was a serious performance bottleneck -> alength is counting the whole array?
;(def ^:private width alength)
;(def ^:private height (comp alength first))

; does not show warning on reflection, but shows cast-double a lot.
(defrecord ArrRayCaster [arr width height]
  PFastRayCaster
  (fast-ray-blocked? [_ [start-x start-y] [target-x target-y]]
    (RayCaster/rayBlocked (double start-x)
                          (double start-y)
                          (double target-x)
                          (double target-y)
                          width ;(width boolean-2d-array)
                          height ;(height boolean-2d-array)
                          arr)))

#_(defn ray-steplist [boolean-2d-array [start-x start-y] [target-x target-y]]
  (seq
   (RayCaster/castSteplist start-x
                           start-y
                           target-x
                           target-y
                           (width boolean-2d-array)
                           (height boolean-2d-array)
                           boolean-2d-array)))

#_(defn ray-maxsteps [boolean-2d-array  [start-x start-y] [vector-x vector-y] max-steps]
  (let [erg (RayCaster/castMaxSteps start-x
                                    start-y
                                    vector-x
                                    vector-y
                                    (width boolean-2d-array)
                                    (height boolean-2d-array)
                                    boolean-2d-array
                                    max-steps
                                    max-steps)]
    (if (= -1 erg)
      :not-blocked
      erg)))

; STEPLIST TEST

#_(def current-steplist (atom nil))

#_(defn steplist-contains? [tilex tiley] ; use vector equality
  (some
    (fn [[x y]]
      (and (= x tilex) (= y tiley)))
    @current-steplist))

#_(defn render-line-middle-to-mouse [color]
  (let [[x y] (input/get-mouse-pos)]
    (g/draw-line (/ (g/viewport-width) 2)
                 (/ (g/viewport-height) 2)
                 x y color)))

#_(defn update-test-raycast-steplist []
    (reset! current-steplist
            (map
             (fn [step]
               [(.x step) (.y step)])
             (raycaster/ray-steplist (get-cell-blocked-boolean-array)
                                     (:position @world/player)
                                     (g/map-coords)))))

;; MAXSTEPS TEST

#_(def current-steps (atom nil))

#_(defn update-test-raycast-maxsteps []
    (let [maxsteps 10]
      (reset! current-steps
              (raycaster/ray-maxsteps (get-cell-blocked-boolean-array)
                                      (v-direction (g/map-coords) start)
                                      maxsteps))))

#_(defn draw-test-raycast []
  (let [start (:position @world/player)
        target (g/map-coords)
        color (if (fast-ray-blocked? start target) g/red g/green)]
    (render-line-middle-to-mouse color)))

; PATH BLOCKED TEST

#_(defn draw-test-path-blocked [] ; TODO draw in map no need for screenpos-of-tilepos
  (let [[start-x start-y] (:position @world/player)
        [target-x target-y] (g/map-coords)
        [start1 target1 start2 target2] (create-double-ray-endpositions start-x start-y target-x target-y 0.4)
        [start1screenx,start1screeny]   (screenpos-of-tilepos start1)
        [target1screenx,target1screeny] (screenpos-of-tilepos target1)
        [start2screenx,start2screeny]   (screenpos-of-tilepos start2)
        [target2screenx,target2screeny] (screenpos-of-tilepos target2)
        color (if (is-path-blocked? start1 target1 start2 target2)
                g/red
                g/green)]
    (g/draw-line start1screenx start1screeny target1screenx target1screeny color)
    (g/draw-line start2screenx start2screeny target2screenx target2screeny color)))

(defn find-first
  "Returns the first item of coll for which (pred item) returns logical true.
  Consumes sequences up to the first match, will consume the entire sequence
  and return nil if no match is found."
  [pred coll]
  (first (filter pred coll)))

(defn ->tile [position]
  (mapv int position))

(defn tile->middle [position]
  (mapv (partial + 0.5) position))

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

(defn get-namespaces [packages]
  (filter #(packages (first (str/split (name (ns-name %)) #"\.")))
          (all-ns)))

(defn get-vars [nmspace condition]
  (for [[sym avar] (ns-interns nmspace)
        :when (condition avar)]
    avar))

(defn exit-app!         [] (.exit               Gdx/app))
(defn delta-time        [] (.getDeltaTime       Gdx/graphics))
(defn frames-per-second [] (.getFramesPerSecond Gdx/graphics))

(defmacro post-runnable! [& exprs]
  `(.postRunnable Gdx/app (fn [] ~@exprs)))

(defn- ->gdx-field [klass-str k]
  (eval (symbol (str "com.badlogic.gdx." klass-str "/" (str/replace (str/upper-case (name k)) "-" "_")))))

(def ^:private ->gdx-input-button (partial ->gdx-field "Input$Buttons"))
(def ^:private ->gdx-input-key    (partial ->gdx-field "Input$Keys"))

(defn button-just-pressed?
  ":left, :right, :middle, :back or :forward."
  [b]
  (.isButtonJustPressed Gdx/input (->gdx-input-button b)))

(defn key-just-pressed?
  "See [[key-pressed?]]."
  [k]
  (.isKeyJustPressed Gdx/input (->gdx-input-key k)))

(defn key-pressed?
  "For options see [libgdx Input$Keys docs](https://javadoc.io/doc/com.badlogicgames.gdx/gdx/latest/com/badlogic/gdx/Input.Keys.html).
  Keys are upper-cased and dashes replaced by underscores.
  For example Input$Keys/ALT_LEFT can be used with :alt-left.
  Numbers via :num-3, etc."
  [k]
  (.isKeyPressed Gdx/input (->gdx-input-key k)))

(defn- set-input-processor! [processor]
  (.setInputProcessor Gdx/input processor))

(defn- kw->color [k] (->gdx-field "graphics.Color" k))

(def white Color/WHITE)
(def black Color/BLACK)

(defn ->color
  ([r g b] (->color r g b 1))
  ([r g b a] (Color. (float r) (float g) (float b) (float a))))

(defn- munge-color ^Color [color]
  (cond (= Color (class color)) color
        (keyword? color) (kw->color color)
        (vector? color) (apply ->color color)
        :else (throw (ex-info "Cannot understand color" {:color color}))))

(defn def-markup-color
  "A general purpose class containing named colors that can be changed at will. For example, the markup language defined by the BitmapFontCache class uses this class to retrieve colors and the user can define his own colors.

  [javadoc](https://javadoc.io/doc/com.badlogicgames.gdx/gdx/latest/com/badlogic/gdx/graphics/Colors.html)"
  [name-str color]
  (Colors/put name-str (munge-color color)))

(def dispose! Disposable/.dispose)

(defn- internal-file ^FileHandle [path]
  (.internal Gdx/files path))

(defn- recursively-search [folder extensions]
  (loop [[^FileHandle file & remaining] (.list (internal-file folder))
         result []]
    (cond (nil? file)
          result

          (.isDirectory file)
          (recur (concat remaining (.list file)) result)

          (extensions (.extension file))
          (recur remaining (conj result (.path file)))

          :else
          (recur remaining result))))

(defn- search-files [folder file-extensions]
  (map #(str/replace-first % folder "")
       (recursively-search folder file-extensions)))

(defn- load-assets [^AssetManager manager files class-k]
  (let [^Class klass (case class-k :sound Sound :texture Texture)]
    (doseq [file files]
      (.load manager ^String file klass))))

(defn- asset-manager []
  (proxy [AssetManager clojure.lang.ILookup] []
    (valAt [file]
      (.get ^AssetManager this ^String file))))

(declare assets
         all-sound-files
         all-texture-files)

(defn load-assets! [folder]
  (let [manager (asset-manager)
        sound-files   (search-files folder #{"wav"})
        texture-files (search-files folder #{"png" "bmp"})]
    (load-assets manager sound-files   :sound)
    (load-assets manager texture-files :texture)
    (.finishLoading manager)
    (bind-root #'assets manager)
    (bind-root #'all-sound-files sound-files)
    (bind-root #'all-texture-files texture-files)))

(defn dispose-assets! []
  (dispose! assets))

(defn play-sound! [path] (Sound/.play (get assets path)))

(defc :tx/sound {:data :sound}
  (do! [[_ file]] (play-sound! file) nil))

(defn camera-position
  "Returns camera position as [x y] vector."
  [^Camera camera]
  [(.x (.position camera))
   (.y (.position camera))])

(defn camera-set-position!
  "Sets x and y and calls update on the camera."
  [^Camera camera [x y]]
  (set! (.x (.position camera)) (float x))
  (set! (.y (.position camera)) (float y))
  (.update camera))

(defn frustum [^Camera camera]
  (let [frustum-points (for [^Vector3 point (take 4 (.planePoints (.frustum camera)))
                             :let [x (.x point)
                                   y (.y point)]]
                         [x y])
        left-x   (apply min (map first  frustum-points))
        right-x  (apply max (map first  frustum-points))
        bottom-y (apply min (map second frustum-points))
        top-y    (apply max (map second frustum-points))]
    [left-x right-x bottom-y top-y]))

(defn visible-tiles [camera]
  (let [[left-x right-x bottom-y top-y] (frustum camera)]
    (for  [x (range (int left-x)   (int right-x))
           y (range (int bottom-y) (+ 2 (int top-y)))]
      [x y])))

(defn calculate-zoom
  "calculates the zoom value for camera to see all the 4 points."
  [^Camera camera & {:keys [left top right bottom]}]
  (let [viewport-width  (.viewportWidth  camera)
        viewport-height (.viewportHeight camera)
        [px py] (camera-position camera)
        px (float px)
        py (float py)
        leftx (float (left 0))
        rightx (float (right 0))
        x-diff (max (- px leftx) (- rightx px))
        topy (float (top 1))
        bottomy (float (bottom 1))
        y-diff (max (- topy py) (- py bottomy))
        vp-ratio-w (/ (* x-diff 2) viewport-width)
        vp-ratio-h (/ (* y-diff 2) viewport-height)
        new-zoom (max vp-ratio-w vp-ratio-h)]
    new-zoom))

(defn zoom [^OrthographicCamera camera]
  (.zoom camera))

(defn set-zoom!
  "Sets the zoom value and updates."
  [^OrthographicCamera camera amount]
  (set! (.zoom camera) amount)
  (.update camera))

(defn reset-zoom!
  "Sets the zoom value to 1."
  [camera]
  (set-zoom! camera 1))

(defn- ->camera ^OrthographicCamera [] (OrthographicCamera.))

; touch coordinates are y-down, while screen coordinates are y-up
; so the clamping of y is reverse, but as black bars are equal it does not matter
(defn- unproject-mouse-posi
  "Returns vector of [x y]."
  [^Viewport viewport]
  (let [mouse-x (clamp (.getX Gdx/input)
                       (.getLeftGutterWidth viewport)
                       (.getRightGutterX viewport))
        mouse-y (clamp (.getY Gdx/input)
                       (.getTopGutterHeight viewport)
                       (.getTopGutterY viewport))
        coords (.unproject viewport (Vector2. mouse-x mouse-y))]
    [(.x coords) (.y coords)]))

(defn- ->gui-viewport [world-width world-height]
  (FitViewport. world-width world-height (->camera)))

(defn- ->world-viewport [world-width world-height unit-scale]
  (let [world-width  (* world-width  unit-scale)
        world-height (* world-height unit-scale)
        camera (->camera)
        y-down? false]
    (.setToOrtho camera y-down? world-width world-height)
    (FitViewport. world-width world-height camera)))

(defn- vp-world-width  [^Viewport vp] (.getWorldWidth  vp))
(defn- vp-world-height [^Viewport vp] (.getWorldHeight vp))
(defn- vp-camera       [^Viewport vp] (.getCamera      vp))
(defn- vp-update!      [^Viewport vp [w h] & {:keys [center-camera?]}]
  (.update vp w h (boolean center-camera?)))

(defn ->texture-region
  ([path-or-texture]
   (let [^Texture tex (if (string? path-or-texture)
                        (get assets path-or-texture)
                        path-or-texture)]
     (TextureRegion. tex)))

  ([^TextureRegion texture-region [x y w h]]
   (TextureRegion. texture-region (int x) (int y) (int w) (int h))))

(defn texture-region-dimensions [^TextureRegion texture-region]
  [(.getRegionWidth  texture-region)
   (.getRegionHeight texture-region)])

(declare ^:private batch)

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

(defn- draw-with! [^Batch batch ^Viewport viewport draw-fn]
  (.setColor batch Color/WHITE) ; fix scene2d.ui.tooltip flickering
  (.setProjectionMatrix batch (.combined (.getCamera viewport)))
  (.begin batch)
  (draw-fn)
  (.end batch))

(defn- ->shape-drawer [batch]
  (let [^Texture tex (let [pixmap (doto (Pixmap. 1 1 Pixmap$Format/RGBA8888)
                                    (.setColor Color/WHITE)
                                    (.drawPixel 0 0))
                           tex (Texture. pixmap)]
                       (dispose! pixmap)
                       tex)]
    {:shape-drawer (ShapeDrawer. batch (TextureRegion. tex 1 0 1 1))
     :shape-drawer-texture tex}))

(defn- set-color!          [^ShapeDrawer sd color] (.setColor sd (munge-color color)))
(defn- sd-ellipse          [^ShapeDrawer sd [x y] radius-x radius-y] (.ellipse sd (float x) (float y) (float radius-x) (float radius-y)))
(defn- sd-filled-ellipse   [^ShapeDrawer sd [x y] radius-x radius-y] (.filledEllipse sd (float x) (float y) (float radius-x) (float radius-y)))
(defn- sd-circle           [^ShapeDrawer sd [x y] radius] (.circle sd (float x) (float y) (float radius)))
(defn- sd-filled-circle    [^ShapeDrawer sd [x y] radius] (.filledCircle sd (float x) (float y) (float radius)))
(defn- sd-arc              [^ShapeDrawer sd [centre-x centre-y] radius start-angle degree] (.arc sd centre-x centre-y radius (degree->radians start-angle) (degree->radians degree)))
(defn- sd-sector           [^ShapeDrawer sd [centre-x centre-y] radius start-angle degree] (.sector sd centre-x centre-y radius (degree->radians start-angle) (degree->radians degree)))
(defn- sd-rectangle        [^ShapeDrawer sd x y w h] (.rectangle sd x y w h))
(defn- sd-filled-rectangle [^ShapeDrawer sd x y w h] (.filledRectangle sd (float x) (float y) (float w) (float h)) )
(defn- sd-line             [^ShapeDrawer sd [sx sy] [ex ey]] (.line sd (float sx) (float sy) (float ex) (float ey)))

(defn- sd-grid [sd leftx bottomy gridw gridh cellw cellh]
  (let [w (* (float gridw) (float cellw))
        h (* (float gridh) (float cellh))
        topy (+ (float bottomy) (float h))
        rightx (+ (float leftx) (float w))]
    (doseq [idx (range (inc (float gridw)))
            :let [linex (+ (float leftx) (* (float idx) (float cellw)))]]
      (sd-line sd [linex topy] [linex bottomy]))
    (doseq [idx (range (inc (float gridh)))
            :let [liney (+ (float bottomy) (* (float idx) (float cellh)))]]
      (sd-line sd [leftx liney] [rightx liney]))))

(defn- sd-with-line-width [^ShapeDrawer sd width draw-fn]
  (let [old-line-width (.getDefaultLineWidth sd)]
    (.setDefaultLineWidth sd (float (* (float width) old-line-width)))
    (draw-fn)
    (.setDefaultLineWidth sd (float old-line-width))))

(defn- ->ttf-params [size quality-scaling]
  (let [params (FreeTypeFontGenerator$FreeTypeFontParameter.)]
    (set! (.size params) (* size quality-scaling))
    ; .color and this:
    ;(set! (.borderWidth parameter) 1)
    ;(set! (.borderColor parameter) red)
    (set! (.minFilter params) Texture$TextureFilter/Linear) ; because scaling to world-units
    (set! (.magFilter params) Texture$TextureFilter/Linear)
    params))

(defn- generate-ttf [{:keys [file size quality-scaling]}]
  (let [generator (FreeTypeFontGenerator. (internal-file file))
        font (.generateFont generator (->ttf-params size quality-scaling))]
    (dispose! generator)
    (.setScale (.getData font) (float (/ quality-scaling)))
    (set! (.markupEnabled (.getData font)) true)
    (.setUseIntegerPositions font false) ; otherwise scaling to world-units (/ 1 48)px not visible
    font))

(defn- gdx-default-font [] (BitmapFont.))

(defn- text-height [^BitmapFont font text]
  (-> text
      (str/split #"\n")
      count
      (* (.getLineHeight font))))

(defn- font-draw [^BitmapFont font
                  unit-scale
                  batch
                  {:keys [x y text h-align up? scale]}]
  (let [data (.getData font)
        old-scale (float (.scaleX data))]
    (.setScale data (* old-scale (float unit-scale) (float (or scale 1))))
    (.draw font
           batch
           (str text)
           (float x)
           (+ (float y) (float (if up? (text-height font text) 0)))
           (float 0) ; target-width
           (case (or h-align :center)
             :center Align/center
             :left   Align/left
             :right  Align/right)
           false) ; wrap false, no need target-width
    (.setScale data old-scale)))

(declare ^:private shape-drawer
         ^:private shape-drawer-texture)

(defn draw-ellipse [position radius-x radius-y color]
  (set-color! shape-drawer color)
  (sd-ellipse shape-drawer position radius-x radius-y))
(defn draw-filled-ellipse [position radius-x radius-y color]
  (set-color! shape-drawer color)
  (sd-filled-ellipse shape-drawer position radius-x radius-y))
(defn draw-circle [position radius color]
  (set-color! shape-drawer color)
  (sd-circle shape-drawer position radius))
(defn draw-filled-circle [position radius color]
  (set-color! shape-drawer color)
  (sd-filled-circle shape-drawer position radius))
(defn draw-arc [center radius start-angle degree color]
  (set-color! shape-drawer color)
  (sd-arc shape-drawer center radius start-angle degree))
(defn draw-sector [center radius start-angle degree color]
  (set-color! shape-drawer color)
  (sd-sector shape-drawer center radius start-angle degree))
(defn draw-rectangle [x y w h color]
  (set-color! shape-drawer color)
  (sd-rectangle shape-drawer x y w h))
(defn draw-filled-rectangle [x y w h color]
  (set-color! shape-drawer color)
  (sd-filled-rectangle shape-drawer x y w h))
(defn draw-line [start end color]
  (set-color! shape-drawer color)
  (sd-line shape-drawer start end))
(defn draw-grid [leftx bottomy gridw gridh cellw cellh color]
  (set-color! shape-drawer color)
  (sd-grid shape-drawer leftx bottomy gridw gridh cellw cellh))
(defn with-shape-line-width [width draw-fn]
  (sd-with-line-width shape-drawer width draw-fn))

(defn- ->gui-view [{:keys [world-width world-height]}]
  {:unit-scale 1
   :viewport (->gui-viewport world-width world-height)})

(defn- ->world-view [{:keys [world-width world-height tile-size]}]
  (let [unit-scale (/ tile-size)]
    {:unit-scale (float unit-scale)
     :viewport (->world-viewport world-width world-height unit-scale)}))

(declare ^:private gui-view
         ^:private world-view)

(defn- bind-views! [{:keys [gui-view world-view]}]
  (bind-root #'gui-view (->gui-view gui-view))
  (bind-root #'world-view (->world-view world-view)))

(defn world-unit-scale []
  (:unit-scale world-view))

(defn pixels->world-units [pixels]
  (* (int pixels) (world-unit-scale)))

(defn- gui-viewport   [] (:viewport gui-view))
(defn- world-viewport [] (:viewport world-view))

(defn- gui-mouse-position* []
  ; TODO mapv int needed?
  (mapv int (unproject-mouse-posi (gui-viewport))))

(defn- world-mouse-position* []
  ; TODO clamping only works for gui-viewport ? check. comment if true
  ; TODO ? "Can be negative coordinates, undefined cells."
  (unproject-mouse-posi (world-viewport)))

(defn gui-mouse-position    [] (gui-mouse-position*))
(defn world-mouse-position  [] (world-mouse-position*))
(defn gui-viewport-width    [] (vp-world-width  (gui-viewport)))
(defn gui-viewport-height   [] (vp-world-height (gui-viewport)))
(defn world-camera          [] (vp-camera       (world-viewport)))
(defn world-viewport-width  [] (vp-world-width  (world-viewport)))
(defn world-viewport-height [] (vp-world-height (world-viewport)))

(defrecord Sprite [texture-region
                   pixel-dimensions
                   world-unit-dimensions
                   color]) ; optional

(declare ^:private ^:dynamic *unit-scale*)

(defn- unit-dimensions [image]
  (if (= *unit-scale* 1)
    (:pixel-dimensions image)
    (:world-unit-dimensions image)))

(defn- scale-dimensions [dimensions scale]
  (mapv (comp float (partial * scale)) dimensions))

(defn- assoc-dimensions
  "scale can be a number for multiplying the texture-region-dimensions or [w h]."
  [{:keys [texture-region] :as image} scale]
  {:pre [(or (number? scale)
             (and (vector? scale)
                  (number? (scale 0))
                  (number? (scale 1))))]}
  (let [pixel-dimensions (if (number? scale)
                           (scale-dimensions (texture-region-dimensions texture-region) scale)
                           scale)]
    (assoc image
           :pixel-dimensions pixel-dimensions
           :world-unit-dimensions (scale-dimensions pixel-dimensions (world-unit-scale)))))

(defn draw-image [{:keys [texture-region color] :as image} position]
  (draw-texture-region batch
                       texture-region
                       position
                       (unit-dimensions image)
                       0 ; rotation
                       color))

(defn draw-rotated-centered-image
  [{:keys [texture-region color] :as image} rotation [x y]]
  (let [[w h] (unit-dimensions image)]
    (draw-texture-region batch
                         texture-region
                         [(- (float x) (/ (float w) 2))
                          (- (float y) (/ (float h) 2))]
                         [w h]
                         rotation
                         color)))

(defn draw-centered-image [image position]
  (draw-rotated-centered-image image 0 position))

(defn- ->image* [texture-region]
  (-> {:texture-region texture-region}
      (assoc-dimensions 1) ; = scale 1
      map->Sprite))

(defn ->image [file]
  (->image* (->texture-region file)))

(defn sub-image [{:keys [texture-region]} bounds]
  (->image* (->texture-region texture-region bounds)))

(defn sprite-sheet [file tilew tileh]
  {:image (->image file)
   :tilew tilew
   :tileh tileh})

(defn sprite
  "x,y index starting top-left"
  [{:keys [image tilew tileh]} [x y]]
  (sub-image image [(* x tilew) (* y tileh) tilew tileh]))

(defn edn->image [{:keys [file sub-image-bounds]}]
  (if sub-image-bounds
    (let [[sprite-x sprite-y] (take 2 sub-image-bounds)
          [tilew tileh]       (drop 2 sub-image-bounds)]
      (sprite (sprite-sheet file tilew tileh)
              [(int (/ sprite-x tilew))
               (int (/ sprite-y tileh))]))
    (->image file)))

(defn- ->default-font [true-type-font]
  (or (and true-type-font (generate-ttf true-type-font))
      (gdx-default-font)))

(declare ^:private default-font)

(defn draw-text
  "font, h-align, up? and scale are optional.
  h-align one of: :center, :left, :right. Default :center.
  up? renders the font over y, otherwise under.
  scale will multiply the drawn text size with the scale."
  [{:keys [x y text font h-align up? scale] :as opts}]
  (font-draw (or font default-font) *unit-scale* batch opts))

(defn- mapvals [f m]
  (into {} (for [[k v] m]
             [k (f v)])))

(defn- ->cursor [file [hotspot-x hotspot-y]]
  (let [pixmap (Pixmap. (internal-file file))
        cursor (.newCursor Gdx/graphics pixmap hotspot-x hotspot-y)]
    (dispose! pixmap)
    cursor))

(defn- ->cursors [cursors]
  (mapvals (fn [[file hotspot]]
             (->cursor (str "cursors/" file ".png") hotspot))
           cursors))

(declare ^:private cursors )

(defn set-cursor! [cursor-key]
  (.setCursor Gdx/graphics (safe-get cursors cursor-key)))

(defc :tx/cursor
  (do! [[_ cursor-key]]
    (set-cursor! cursor-key)
    nil))

(defn- render-view! [{:keys [viewport unit-scale]} draw-fn]
  (draw-with! batch
              viewport
              (fn []
                (with-shape-line-width unit-scale
                  #(binding [*unit-scale* unit-scale]
                     (draw-fn))))))

(defn render-gui-view!   [render-fn] (render-view! gui-view render-fn))
(defn render-world-view! [render-fn] (render-view! world-view render-fn))

(defn update-viewports! [dim]
  (vp-update! (gui-viewport) dim :center-camera? true)
  (vp-update! (world-viewport) dim)) ; Do not center the camera on world-viewport. We set the position there manually.

(declare draw-item-on-cursor)

(declare ^:private cached-map-renderer)

(defn draw-tiled-map
  "Renders tiled-map using world-view at world-camera position and with world-unit-scale.

  Color-setter is a `(fn [color x y])` which is called for every tile-corner to set the color.

  Can be used for lights & shadows.

  Renders only visible layers."
  [tiled-map color-setter]
  (t/render-tm! (cached-map-renderer tiled-map)
                color-setter
                (world-camera)
                tiled-map))

(defn- ->tiled-map-renderer [tiled-map]
  (t/->orthogonal-tiled-map-renderer tiled-map (world-unit-scale) batch))

(defn load-graphics! [{:keys [views default-font cursors]}]
  (let [batch (SpriteBatch.)
        {:keys [shape-drawer shape-drawer-texture]} (->shape-drawer batch)]
    (bind-root #'batch batch)
    (bind-root #'shape-drawer shape-drawer)
    (bind-root #'shape-drawer-texture shape-drawer-texture)
    (bind-root #'cursors (->cursors cursors))
    (bind-root #'default-font (->default-font default-font))
    (bind-views! views)
    (bind-root #'cached-map-renderer (memoize ->tiled-map-renderer))))

(defn dispose-graphics! []
  (dispose! batch)
  (dispose! shape-drawer-texture)
  (dispose! default-font)
  (run! dispose! (vals cursors)))

(declare ^:private screen-k
         ^:private screens)

(defn current-screen []
  [screen-k (screen-k screens)])

(defsystem screen-enter "FIXME" [_])
(defmethod screen-enter :default [_])

(defsystem screen-exit  "FIXME" [_])
(defmethod screen-exit :default  [_])

(defn change-screen
  "Calls `screen-exit` on the current-screen (if there is one).
  Calls `screen-enter` on the new screen."
  [new-k]
  (when-let [v (and (bound? #'screen-k) (screen-k screens))]
    (screen-exit [screen-k v]))
  (let [v (new-k screens)]
    (assert v (str "Cannot find screen with key: " new-k))
    (bind-root #'screen-k new-k)
    (screen-enter [new-k v])))

(defsystem screen-render! "FIXME" [_])

(defsystem screen-render "FIXME" [_])
(defmethod screen-render :default [_])

(defn create-vs
  "Creates a map for every component with map entries `[k (->mk [k v])]`."
  [components]
  (reduce (fn [m [k v]]
            (assoc m k (->mk [k v])))
          {}
          components))

(defn load-screens! [screen-ks]
  (bind-root #'screens (create-vs (zipmap screen-ks (repeat nil))))
  (change-screen (ffirst screens)))

(defn dispose-screens! []
  ; TODO screens not disposed https://github.com/damn/core/issues/41
  )

(defn- check-cleanup-visui! []
  ; app crashes during startup before VisUI/dispose and we do clojure.tools.namespace.refresh-> gui elements not showing.
  ; => actually there is a deeper issue at play
  ; we need to dispose ALL resources which were loaded already ...
  (when (VisUI/isLoaded)
    (VisUI/dispose)))

(defn- font-enable-markup! []
  (-> (VisUI/getSkin)
      (.getFont "default-font")
      .getData
      .markupEnabled
      (set! true)))

(defn- set-tooltip-config! []
  (set! Tooltip/DEFAULT_APPEAR_DELAY_TIME (float 0))
  ;(set! Tooltip/DEFAULT_FADE_TIME (float 0.3))
  ;Controls whether to fade out tooltip when mouse was moved. (default false)
  ;(set! Tooltip/MOUSE_MOVED_FADEOUT true)
  )

(defn load-ui! [skin-scale]
  (check-cleanup-visui!)
  (VisUI/load (case skin-scale
                :skin-scale/x1 VisUI$SkinScale/X1
                :skin-scale/x2 VisUI$SkinScale/X2))
  (font-enable-markup!)
  (set-tooltip-config!))

(defn dispose-ui! []
  (VisUI/dispose))

(defn actor-x [^Actor a] (.getX a))
(defn actor-y [^Actor a] (.getY a))

(defn actor-id [^Actor actor]
  (.getUserObject actor))

(defn set-id! [^Actor actor id]
  (.setUserObject actor id))

(defn set-name! [^Actor actor name]
  (.setName actor name))

(defn actor-name [^Actor actor]
  (.getName actor))

(defn visible? [^Actor actor] ; used
  (.isVisible actor))

(defn set-visible! [^Actor actor bool]
  (.setVisible actor (boolean bool)))

(defn toggle-visible! [actor] ; used
  (set-visible! actor (not (visible? actor))))

(defn set-position! [^Actor actor x y]
  (.setPosition actor x y))

(defn set-center! [^Actor actor x y]
  (set-position! actor
                 (- x (/ (.getWidth actor) 2))
                 (- y (/ (.getHeight actor) 2))))

(defn set-touchable!
  ":children-only, :disabled or :enabled."
  [^Actor actor touchable]
  (.setTouchable actor (case touchable
                         :children-only Touchable/childrenOnly
                         :disabled      Touchable/disabled
                         :enabled       Touchable/enabled)))

(defn add-listener! [^Actor actor listener]
  (.addListener actor listener))

(defn remove!
  "Removes this actor from its parent, if it has a parent."
  [^Actor actor]
  (.remove actor))

(defn parent
  "Returns the parent actor, or null if not in a group."
  [^Actor actor]
  (.getParent actor))

(defn a-mouseover? [^Actor actor [x y]]
  (let [v (.stageToLocalCoordinates actor (Vector2. x y))]
    (.hit actor (.x v) (.y v) true)))

(defn remove-tooltip! [^Actor actor]
  (Tooltip/removeTooltip actor))

(defn find-ancestor-window ^Window [^Actor actor]
  (if-let [p (parent actor)]
    (if (instance? Window p)
      p
      (find-ancestor-window p))
    (throw (Error. (str "Actor has no parent window " actor)))))

(defn pack-ancestor-window! [^Actor actor]
  (.pack (find-ancestor-window actor)))

(defn children
  "Returns an ordered list of child actors in this group."
  [^Group group]
  (seq (.getChildren group)))

(defn clear-children!
  "Removes all actors from this group and unfocuses them."
  [^Group group]
  (.clearChildren group))

(defn add-actor!
  "Adds an actor as a child of this group, removing it from its previous parent. If the actor is already a child of this group, no changes are made."
  [^Group group actor]
  (.addActor group actor))

(defn find-actor-with-id [group id]
  (let [actors (children group)
        ids (keep actor-id actors)]
    (assert (or (empty? ids)
                (apply distinct? ids)) ; TODO could check @ add
            (str "Actor ids are not distinct: " (vec ids)))
    (first (filter #(= id (actor-id %)) actors))))

(defn set-cell-opts [^Cell cell opts]
  (doseq [[option arg] opts]
    (case option
      :fill-x?    (.fillX     cell)
      :fill-y?    (.fillY     cell)
      :expand?    (.expand    cell)
      :expand-x?  (.expandX   cell)
      :expand-y?  (.expandY   cell)
      :bottom?    (.bottom    cell)
      :colspan    (.colspan   cell (int arg))
      :pad        (.pad       cell (float arg))
      :pad-top    (.padTop    cell (float arg))
      :pad-bottom (.padBottom cell (float arg))
      :width      (.width     cell (float arg))
      :height     (.height    cell (float arg))
      :right?     (.right     cell)
      :left?      (.left      cell))))

(defn add-rows!
  "rows is a seq of seqs of columns.
  Elements are actors or nil (for just adding empty cells ) or a map of
  {:actor :expand? :bottom?  :colspan int :pad :pad-bottom}. Only :actor is required."
  [^Table table rows]
  (doseq [row rows]
    (doseq [props-or-actor row]
      (cond
       (map? props-or-actor) (-> (.add table ^Actor (:actor props-or-actor))
                                 (set-cell-opts (dissoc props-or-actor :actor)))
       :else (.add table ^Actor props-or-actor)))
    (.row table))
  table)

(defn- set-table-opts [^Table table {:keys [rows cell-defaults]}]
  (set-cell-opts (.defaults table) cell-defaults)
  (add-rows! table rows))

(defn t-row!   "Add row to table." [^Table t] (.row   t))
(defn t-clear! "Clear table. "     [^Table t] (.clear t))

(defn t-add!
  "Add to table"
  ([^Table t] (.add t))
  ([^Table t ^Actor a] (.add t a)))

(defn ->horizontal-separator-cell [colspan]
  {:actor (Separator. "default")
   :pad-top 2
   :pad-bottom 2
   :colspan colspan
   :fill-x? true
   :expand-x? true})

(defn ->vertical-separator-cell []
  {:actor (Separator. "vertical")
   :pad-top 2
   :pad-bottom 2
   :fill-y? true
   :expand-y? true})

; candidate for opts: :tooltip
(defn- set-actor-opts [actor {:keys [id name visible? touchable center-position position] :as opts}]
  (when id   (set-id!   actor id))
  (when name (set-name! actor name))
  (when (contains? opts :visible?)  (set-visible! actor visible?))
  (when touchable (set-touchable! actor touchable))
  (when-let [[x y] center-position] (set-center!   actor x y))
  (when-let [[x y] position]        (set-position! actor x y))
  actor)

(comment
 ; fill parent & pack is from Widget TODO ( not widget-group ?)
 com.badlogic.gdx.scenes.scene2d.ui.Widget
 ; about .pack :
 ; Generally this method should not be called in an actor's constructor because it calls Layout.layout(), which means a subclass would have layout() called before the subclass' constructor. Instead, in constructors simply set the actor's size to Layout.getPrefWidth() and Layout.getPrefHeight(). This allows the actor to have a size at construction time for more convenient use with groups that do not layout their children.
 )

(defn- set-widget-group-opts [^WidgetGroup widget-group {:keys [fill-parent? pack?]}]
  (.setFillParent widget-group (boolean fill-parent?)) ; <- actor? TODO
  (when pack?
    (.pack widget-group))
  widget-group)

(defn- set-opts [actor opts]
  (set-actor-opts actor opts)
  (when (instance? Table actor)
    (set-table-opts actor opts)) ; before widget-group-opts so pack is packing rows
  (when (instance? WidgetGroup actor)
    (set-widget-group-opts actor opts))
  actor)

#_(defn- add-window-close-button [^Window window]
    (.add (.getTitleTable window)
          (text-button "x" #(.setVisible window false)))
    window)

(defmacro ^:private proxy-ILookup
  "For actors inheriting from Group."
  [class args]
  `(proxy [~class clojure.lang.ILookup] ~args
     (valAt
       ([id#]
        (find-actor-with-id ~'this id#))
       ([id# not-found#]
        (or (find-actor-with-id ~'this id#) not-found#)))))

(defn ->group [{:keys [actors] :as opts}]
  (let [group (proxy-ILookup Group [])]
    (run! #(add-actor! group %) actors)
    (set-opts group opts)))

(defn ->horizontal-group [{:keys [space pad]}]
  (let [group (proxy-ILookup HorizontalGroup [])]
    (when space (.space group (float space)))
    (when pad   (.pad   group (float pad)))
    group))

(defn ->vertical-group [actors]
  (let [group (proxy-ILookup VerticalGroup [])]
    (run! #(add-actor! group %) actors)
    group))

(defn ->button-group
  "https://javadoc.io/doc/com.badlogicgames.gdx/gdx/latest/com/badlogic/gdx/scenes/scene2d/ui/ButtonGroup.html"
  [{:keys [max-check-count min-check-count]}]
  (let [button-group (ButtonGroup.)]
    (.setMaxCheckCount button-group max-check-count)
    (.setMinCheckCount button-group min-check-count)
    button-group))

(defn ->check-box
  "on-clicked is a fn of one arg, taking the current isChecked state
  [com.kotcrab.vis.ui.widget.VisCheckBox](https://www.javadoc.io/static/com.kotcrab.vis/vis-ui/1.5.3/com/kotcrab/vis/ui/widget/VisCheckBox.html)"
  [text on-clicked checked?]
  (let [^Button button (VisCheckBox. ^String text)]
    (.setChecked button checked?)
    (.addListener button
                  (proxy [ChangeListener] []
                    (changed [event ^Button actor]
                      (on-clicked (.isChecked actor)))))
    button))

(defn ->select-box [{:keys [items selected]}]
  (doto (VisSelectBox.)
    (.setItems ^"[Lcom.badlogic.gdx.scenes.scene2d.Actor;" (into-array items))
    (.setSelected selected)))

(defn ->table ^Table [opts]
  (-> (proxy-ILookup VisTable [])
      (set-opts opts)))

(defn ->window ^VisWindow [{:keys [title modal? close-button? center? close-on-escape?] :as opts}]
  (-> (let [window (doto (proxy-ILookup VisWindow [^String title true]) ; true = showWindowBorder
                     (.setModal (boolean modal?)))]
        (when close-button?    (.addCloseButton window))
        (when center?          (.centerWindow   window))
        (when close-on-escape? (.closeOnEscape  window))
        window)
      (set-opts opts)))

(defn ->label ^VisLabel [text]
  (VisLabel. ^CharSequence text))

(defn ->text-field [^String text opts]
  (-> (VisTextField. text)
      (set-opts opts)))

; TODO is not decendend of SplitPane anymore => check all type hints here
(defn ->split-pane [{:keys [^Actor first-widget
                            ^Actor second-widget
                            ^Boolean vertical?] :as opts}]
  (-> (VisSplitPane. first-widget second-widget vertical?)
      (set-actor-opts opts)))

(defn ->stack [actors]
  (proxy-ILookup Stack [(into-array Actor actors)]))

(defmulti ^:private ->vis-image type)
(defmethod ->vis-image Drawable      [^Drawable d      ] (VisImage.  d))
(defmethod ->vis-image TextureRegion [^TextureRegion tr] (VisImage. tr))

; TODO widget also make, for fill parent
(defn ->ui-image-widget
  "Takes either a texture-region or drawable. Opts are :scaling, :align and actor opts."
  [object {:keys [scaling align fill-parent?] :as opts}]
  (-> (let [^Image image (->vis-image object)]
        (when (= :center align) (.setAlign image Align/center))
        (when (= :fill scaling) (.setScaling image Scaling/fill))
        (when fill-parent? (.setFillParent image true))
        image)
      (set-opts opts)))

(defn ->image-widget [image opts]
  (->ui-image-widget (:texture-region image) opts))

; => maybe with VisImage not necessary anymore?
(defn ->texture-region-drawable [^TextureRegion texture-region]
  (TextureRegionDrawable. texture-region))

(defn ->scroll-pane [actor]
  (let [scroll-pane (VisScrollPane. actor)]
    (.setFlickScroll scroll-pane false)
    (.setFadeScrollBars scroll-pane false)
    scroll-pane))

(defn- button-class? [actor]
  (some #(= Button %) (supers (class actor))))

(defn button?
  "Returns true if the actor or its parent is a button."
  [actor]
  (or (button-class? actor)
      (and (parent actor)
           (button-class? (parent actor)))))

(defn window-title-bar?
  "Returns true if the actor is a window title bar."
  [actor]
  (when (instance? Label actor)
    (when-let [p (parent actor)]
      (when-let [p (parent p)]
        (and (instance? VisWindow p)
             (= (.getTitleLabel ^Window p) actor))))))

(defn add-tooltip!
  "tooltip-text is a (fn []) or a string. If it is a function will be-recalculated every show."
  [^Actor actor tooltip-text]
  (let [text? (string? tooltip-text)
        label (VisLabel. (if text? tooltip-text ""))
        tooltip (proxy [Tooltip] []
                  ; hooking into getWidth because at
                  ; https://github.com/kotcrab/vis-blob/master/ui/src/main/java/com/kotcrab/vis/ui/widget/Tooltip.java#L271
                  ; when tooltip position gets calculated we setText (which calls pack) before that
                  ; so that the size is correct for the newly calculated text.
                  (getWidth []
                    (let [^Tooltip this this]
                      (when-not text?
                        (.setText this (str (tooltip-text))))
                      (proxy-super getWidth))))]
    (.setAlignment label Align/center)
    (.setTarget  tooltip ^Actor actor)
    (.setContent tooltip ^Actor label)))

(declare ^:dynamic *on-clicked-actor*)

(defn- ->change-listener [on-clicked]
  (proxy [ChangeListener] []
    (changed [event actor]
      (binding [*on-clicked-actor* actor]
        (on-clicked)))))

(defn ->text-button [text on-clicked]
  (let [button (VisTextButton. ^String text)]
    (.addListener button (->change-listener on-clicked))
    button))

(defn- ->ui-image-button [texture-region scale on-clicked]
  (let [drawable (TextureRegionDrawable. ^TextureRegion texture-region)
        button (VisImageButton. drawable)]
    (when scale
      (let [[w h] (texture-region-dimensions texture-region)]
        (.setMinSize drawable (float (* scale w)) (float (* scale h)))))
    (.addListener button (->change-listener on-clicked))
    button))

; TODO check how to make toggle-able ? with hotkeys for actionbar trigger ?
(defn ->image-button
  ([image on-clicked]
   (->image-button image on-clicked {}))

  ([image on-clicked {:keys [scale]}]
   (->ui-image-button (:texture-region image) scale on-clicked)))

(defn- ->ui-actor [draw! act!]
  (proxy [Actor] []
    (draw [_batch _parent-alpha]
      (draw!))
    (act [_delta]
      (act!))))

(defn ->ui-widget [draw!]
  (proxy [Widget] []
    (draw [_batch _parent-alpha]
      (draw! this))))

(defn set-drawable! [^Image image drawable]
  (.setDrawable image drawable))

(defn set-min-size! [^TextureRegionDrawable drawable size]
  (.setMinSize drawable (float size) (float size)))

(defn ->tinted-drawable
  "Creates a new drawable that renders the same as this drawable tinted the specified color."
  [drawable color]
  (.tint ^TextureRegionDrawable drawable color))

(defmacro ->click-listener [& clicked-fn-body]
  `(proxy [ClickListener] []
     ~@clicked-fn-body))

(defn bg-add!    [button-group button] (.add    ^ButtonGroup button-group ^Button button))
(defn bg-remove! [button-group button] (.remove ^ButtonGroup button-group ^Button button))
(defn bg-checked
  "The first checked button, or nil."
  [button-group]
  (.getChecked ^ButtonGroup button-group))

; FIXME t- already used for trees

(defn ->t-node ^Tree$Node [actor]
  (proxy [Tree$Node] [actor]))

(defn ->ui-tree [] (VisTree.))

; FIXME broken
(defn t-node-add! [^Tree$Node parent node] (.add parent node))

(defn- ->ui-stage
  "Stage implements clojure.lang.ILookup (get) on actor id."
  ^Stage [viewport batch]
  (proxy [Stage clojure.lang.ILookup] [viewport batch]
    (valAt
      ([id]
       (find-actor-with-id (.getRoot ^Stage this) id))
      ([id not-found]
       (or (find-actor-with-id (.getRoot ^Stage this) id) not-found)))))

(defn- s-act!   [^Stage s]   (.act      s))
(defn- s-draw!  [^Stage s]   (.draw     s))
(defn  s-root   [^Stage s]   (.getRoot  s))
(defn  s-clear! [^Stage s]   (.clear    s))
(defn  s-add!   [^Stage s a] (.addActor s a))
(defn- s-hit    [^Stage s [x y] & {:keys [touchable?]}]
  (.hit s x y (boolean touchable?)))

; TODO not disposed anymore... screens are sub-level.... look for dispose stuff also in @ cdq! FIXME
(defc :screens/stage
  {:let {:keys [stage sub-screen]}}
  (screen-enter [_]
    (set-input-processor! stage)
    (screen-enter sub-screen))

  (screen-exit [_]
    (set-input-processor! nil)
    (screen-exit sub-screen))

  (screen-render! [_]
    ; stage act first so user-screen calls change-screen -> is the end of frame
    ; otherwise would need render-after-stage
    ; or on change-screen the stage of the current screen would still .act
    (s-act! stage)
    (screen-render sub-screen)
    (s-draw! stage)))

(defn ->stage [actors]
  (let [stage (->ui-stage (:viewport gui-view) batch)]
    (run! #(s-add! stage %) actors)
    stage))

(defn stage-get []
  (:stage ((current-screen) 1)))

(defn mouse-on-actor? []
  (s-hit (stage-get) (gui-mouse-position) :touchable? true))

(defn stage-add! [actor]
  (s-add! (stage-get) actor))

(defn ->actor [{:keys [draw act]}]
  (->ui-actor (fn [] (when draw
                       (binding [*unit-scale* 1]
                         (draw))))
              (fn [] (when act
                       (act)))))

(def ^:private image-file "images/moon_background.png")

(defn ->background-image []
  (->image-widget (->image image-file)
                  {:fill-parent? true
                   :scaling :fill
                   :align :center}))

(defmacro ^:private with-err-str
  "Evaluates exprs in a context in which *err* is bound to a fresh
  StringWriter.  Returns the string created by any nested printing
  calls."
  [& body]
  `(let [s# (new java.io.StringWriter)]
     (binding [*err* s#]
       ~@body
       (str s#))))

(defn error-window! [throwable]
  (binding [*print-level* 5]
    (pretty-pst throwable 24))
  (stage-add! (->window {:title "Error"
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

(defn- show-player-modal! [{:keys [title text button-text on-click]}]
  (assert (not (::modal (stage-get))))
  (stage-add! (->window {:title title
                         :rows [[(->label text)]
                                [(->text-button button-text
                                                (fn []
                                                  (remove! (::modal (stage-get)))
                                                  (on-click)))]]
                         :id ::modal
                         :modal? true
                         :center-position [(/ (gui-viewport-width) 2)
                                           (* (gui-viewport-height) (/ 3 4))]
                         :pack? true})))

(defc :tx/player-modal
  (do! [[_ params]]
    (show-player-modal! params)
    nil))

(defn- lwjgl3-app-config
  [{:keys [title width height full-screen? fps]}]
  {:pre [title width height (boolean? full-screen?) (or (nil? fps) (int? fps))]}
  ; https://github.com/libgdx/libgdx/pull/7361
  ; Maybe can delete this when using that new libgdx version
  ; which includes this PR.
  (when SharedLibraryLoader/isMac
    (.set org.lwjgl.system.Configuration/GLFW_LIBRARY_NAME "glfw_async")
    (.set org.lwjgl.system.Configuration/GLFW_CHECK_THREAD0 false))
  (let [config (doto (Lwjgl3ApplicationConfiguration.)
                 (.setTitle title)
                 (.setForegroundFPS (or fps 60)))]
    (if full-screen?
      (.setFullscreenMode config (Lwjgl3ApplicationConfiguration/getDisplayMode))
      (.setWindowedMode config width height))
    config))

(defn start-app! [& {:keys [resources graphics screen-ks ui] :as config}]
  (Lwjgl3Application. (proxy [ApplicationAdapter] []
                        (create []
                          (load-assets! resources)
                          (load-graphics! graphics)
                          (load-ui! ui)
                          (load-screens! screen-ks))

                        (dispose []
                          (dispose! assets)
                          (dispose-graphics!)
                          (dispose-ui!)
                          (dispose-screens!))

                        (render []
                          (ScreenUtils/clear black)
                          (screen-render! (current-screen)))

                        (resize [w h]
                          (update-viewports! [w h])))
                      (lwjgl3-app-config config)))

(declare entity-tile
         enemy-faction)

(require '[core.world :as world])

(load "world/raycaster"
      "world/grid"
      "world/potential_fields"
      "world/content_grid")

(def mouseover-entity nil)

(defn mouseover-entity* []
  (when-let [entity mouseover-entity]
    @entity))

(declare explored-tile-corners)

(declare ^{:doc "The game logic update delta-time. Different then delta-time-raw because it is bounded by a maximum value for entity movement speed."}
         world-delta

         ^{:doc "The elapsed in-game-time (not counting when game is paused)."}
         elapsed-time

         ^{:doc "The game-logic frame number, starting with 1. (not counting when game is paused)"}
         logic-frame)

(defrecord Counter [duration stop-time])

(defn ->counter [duration]
  {:pre [(>= duration 0)]}
  (->Counter duration (+ elapsed-time duration)))

(defn stopped? [{:keys [stop-time]}]
  (>= elapsed-time stop-time))

(defn reset [{:keys [duration] :as counter}]
  (assoc counter :stop-time (+ elapsed-time duration)))

(defn finished-ratio [{:keys [duration stop-time] :as counter}]
  {:post [(<= 0 % 1)]}
  (if (stopped? counter)
    0
    ; min 1 because floating point math inaccuracies
    (min 1 (/ (- stop-time elapsed-time) duration))))

(load "entity/base"
      "entity/image"
      "entity/animation"
      "entity/movement"
      "entity/delete_after_duration"
      "entity/destroy_audiovisual"
      "entity/line"
      "entity/projectile"
      "entity/skills"
      "entity/faction"
      "entity/clickable"
      "entity/mouseover"
      "entity/temp_modifier"
      "entity/alert"
      "entity/string_effect"
      "entity/modifiers"
      "entity/inventory"
      "entity/state")

{:metadoc/categories {:app "🖥️ Application"
                      :app.graphics "🎨 Graphics"
                      :app.graphics.camera "🎥 Camera"
                      :app.graphics.images "🖼️ Image"
                      :app.screen "📺 Screens"
                      :app.view.gui "🖼️ Gui View"
                      :app.view.world "🗺️ World View"
                      :app.input "🎮 Input"
                      :component "⚙️ Component"
                      :ctx "📜 Context"
                      :effects "💥 Effects"
                      :entity "👾 Entity"
                      :geometry "📐 Geometry"
                      :properties "📦️ Properties"
                      :ui "🎛️ UI"
                      :ui-actor "🕴️ UI Actor"
                      :ui-stage "UI Stage"
                      :ui-table "UI Table"
                      :utils "🔧 Utils"
                      :utils.random "🎲 Random"
                      :world "🌎 World"
                      :world.timer "⏳ Timer"}}

(defn- add-metadoc! []
  (doseq [[doc-cat syms] (edn/read-string (slurp "doc/categories.edn"))
          sym syms]
    (try (alter-meta! (resolve sym) assoc :metadoc/categories #{doc-cat})
         (catch Throwable t
           (throw (ex-info "" {:sym sym} t))))))

(defn- anony-class? [[sym avar]]
  (instance? java.lang.Class @avar))

; TODO only funcs, no macros
; what about record constructors, refer-all -> need to make either private or
; also highlight them ....
; only for categorization not necessary
(defn- vimstuff []
  (spit "vimstuff"
        (apply str
               (remove #{"defc" "defsystem"}
                       (interpose " , " (map str (keys (->> (ns-publics *ns*)
                                                            (remove anony-class?)))))))))

(defn- record-constructor? [[sym avar]]
  (re-find #"(map)?->\p{Upper}" (name sym)))


(defn- relevant-ns-publics []
  (->> (ns-publics *ns*)
       (remove anony-class?)
       (remove record-constructor?)))
; 1. macros separate
; 2. defsystems separate
; 3. 'v-'
; 4. protocols ?1 protocol functions included ?!

#_(spit "testo"
        (str/join "\n"
                  (for [[asym avar] (sort-by first (relevant-ns-publics))]
                    (str asym " " (:arglists (meta avar)))
                    )
                  )
        )

(comment
 (spit "relevant_ns_publics"
       (str/join "\n" (sort (map first (relevant-ns-publics))))))
; = 264 public vars
; next remove ->Foo and map->Foo

#_(let [[asym avar] (first (relevant-ns-publics))]
    (str asym " "(:arglists (meta avar)))
    )

(defn- ->clojurefuncs [fns-strs-names]
  (str "\\   'clojureFunc': [\""  fns-strs-names "\"]"))

; TODO only funcs, no macros
; what about record constructors, refer-all -> need to make either private or
; also highlight them ....
; only for categorization not necessary
(defn ->gdx-public-names-vimrc
  "Without macros `defc` and `defsystem`."
  [file]
  (spit file
        (->clojurefuncs
         (apply str
                (remove #{"defc" "defsystem"}
                        (interpose " , " (map str (keys (->> (ns-interns 'clojure.gdx)
                                                             (remove anony-class?))))))))))

(when (= "true" (System/getenv "ADD_METADOC"))
  (add-metadoc!))
