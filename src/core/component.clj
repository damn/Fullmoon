(ns core.component
  (:refer-clojure :exclude [defn]))

; TODO (keys (methods create-fn)) is weird - what if there are hundreds of fn but only 1 component?
; => we could cache this directly @ core.component/attributes (rename to core.component/components)
; => the intersection of systems for each component
; => can also visualize that then

(def ^:private warn-on-override true)

(defmacro defn
  "Defines a component function with the given parameter vector.
  See also core.component/def.
  Obligatory first parameter: component, a vector of [key/attribute value].
  Dispatching on component attribute."
  [sys-name params]
  (when (zero? (count params))
    (throw (IllegalArgumentException. "First argument needs to be component.")))
  (when warn-on-override
    (when-let [avar (resolve sys-name)]
      (println "WARNING: Overwriting defsystem:" avar)))
  `(do
    (defmulti ~(vary-meta sys-name assoc :params (list 'quote params))
      (fn ~(symbol (str (name sys-name))) [& args#] (ffirst args#)))
    (var ~sys-name)))

(def attributes {})

(defmacro def
  "Defines a component with key 'k'. User-data attr-map.
  v is let-bound over every fn-implementation;

  Example:
  (defcomponent :entity/animation {:schema animation}
    animation
    (entity/render [_ g]
      (g/render-animation animation)))"
  ([k attr-map]
   `(alter-var-root #'attributes assoc ~k ~attr-map))
  ([k attr-map v & sys-impls]
   `(do
     (core.component/def ~k ~attr-map)
     ~@(for [[sys & fn-body] sys-impls
             :let [sys-var (resolve sys)
                   sys-params (:params (meta sys-var))
                   fn-params (first fn-body)
                   method-name (symbol (str (name (symbol sys-var)) "." (name k)))]]
         (do
          (when-not sys-var
            (throw (IllegalArgumentException. (str sys " does not exist."))))
          (when-not (= (count sys-params) (count fn-params)) ; defmethods do not check this, that's why we check it here.
            (throw (IllegalArgumentException.
                    (str sys-var " requires " (count sys-params) " args: " sys-params "."
                         " Given " (count fn-params)  " args: " fn-params))))
          (when (and warn-on-override
                     (get (methods @sys-var) k))
            (println "WARNING: Overwriting defcomponent" k "on" sys-var))
          (when (some #(= % (first fn-params)) (rest fn-params))
            (throw (IllegalArgumentException. (str "First component parameter is shadowed by another parameter at " sys-var))))
          `(defmethod ~sys ~k ~method-name ~fn-params
             (let [~v (~(first fn-params) 1)] ; TODO shadowing _very_ dangerous if in some fns deconstructing [_ foo] then it takes _ ....
               ~@(rest fn-body)))))
     ~k)))

(clojure.core/defn update-map
  "Recursively calls (assoc m k (apply component/fn [k v] args)) for every k of (keys (methods component/fn)),
  which is non-nil/false in m."
  [m multimethod & args]
  (loop [ks (keys (methods multimethod))
         m m]
    (if (seq ks)
      (recur (rest ks)
             (let [k (first ks)]
               (if-let [v (k m)]
                 (assoc m k (apply multimethod [k v] args))
                 m)))
      m)))

(comment
 (defmulti foo ffirst)
 (defmethod foo :bar [[_ v]] (+ v 2))
 (= (update-map {} foo) {})
 (= (update-map {:baz 2} foo) {:baz 2})
 (= (update-map {:baz 2 :bar 0} foo) {:baz 2, :bar 2})
 )

(clojure.core/defn run-system! [system obj & args]
  (doseq [k (keys (methods system))
          :let [v (k obj)]
          :when v]
    (apply system [k v] obj args)))

(clojure.core/defn apply-system [system m & args]
  (for [k (keys (methods system))
        :let [v (k m)]
        :when v]
    (apply system [k v] m args)))

; TODO transducer ?
; transduce
; return xform and only run once over coll ?
#_(defn- apply-system [system m ctx]
  (into []
        ; TODO comp keep ?
        (map (fn [k]
               (let [v (k m)]
                 (when v
                   (system [k v] m ctx)))))
        (keys (methods system))))


; TODO also asserts component exists ! do this maybe first w. schema or sth.

(defn- k->component-ns [k]
  (symbol (str (namespace k) "." (name k))))

(clojure.core/defn load! [components]
  (assert (apply distinct? (map first components)))
  (doseq [[k _] components
          :let [component-ns (k->component-ns k)]]
    (require component-ns)
    (assert (find-ns component-ns)
            (str "Cannot find component namespace " component-ns))))

; TODO or default method return 'value' ....
; => TODO just use (keys system)  ... ?
; TODO why (get component/attributes k)
; TODO similar to update-map
; TODO :context/assets false does still load
; also [:context/assets] w/o args possible
(clojure.core/defn build [obj create-fn components & {:keys [log?]}]
  (reduce (fn [obj {k 0 :as component}]
            (when log? (println k))
            (if (get attributes k)
              (assoc obj k (create-fn component obj))
              obj))
          obj
          components))
