(in-ns 'clojure.ctx)

(def defsystems "Map of all systems as key of name-string to var." {})

(defmacro defsystem
  "A system is a multimethod which takes a component `[k v]` and dispatches on k."
  [sys-name docstring params]
  (when (zero? (count params))
    (throw (IllegalArgumentException. "First argument needs to be component.")))
  (when-let [avar (resolve sys-name)]
    (println "WARNING: Overwriting defsystem:" avar))
  `(do
    (defmulti ~(vary-meta sys-name assoc :params (list 'quote params) ::system? true)
      ~(str "[[defsystem]] with params: `" params "` \n\n " docstring)
      (fn ~(symbol (str (name sys-name))) [[k# _#] & args#] k#))
    (alter-var-root #'defsystems assoc ~(str (ns-name *ns*) "/" sys-name) (var ~sys-name))
    (var ~sys-name)))

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
```clojure
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
