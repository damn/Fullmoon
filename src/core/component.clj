(ns core.component)

; TODO line number for overwrite warnings or ns at least....

(def warn-on-override true)

(def defsystems {})

(defmacro defsystem
  "Defines a component function with the given parameter vector.
  See also core.defcomponent.
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
      (fn ~(symbol (str (name sys-name))) [& args#]
        (ffirst args#)))

    (alter-var-root #'defsystems assoc ~(str (ns-name *ns*) "/" sys-name) (var ~sys-name))

    (var ~sys-name)))

(def attributes {})

(defn- component-systems [component-k]
  (for [[sys-name sys-var] defsystems
        [k method] (methods @sys-var)
        :when (= k component-k)]
    sys-name))

(comment
 (spit "components.txt"
       (with-out-str
        (clojure.pprint/pprint (group-by namespace
                                         (sort (keys attributes))))))

 (ancestors :op/val-inc)

 (spit "components.md"
       (binding [*print-level* nil]
         (with-out-str
          (doseq [[nmsp components] (sort-by first
                                             (group-by namespace
                                                       (sort (keys attributes))))]
            (println "\n#" nmsp)
            (doseq [k components]
              (println "*" k
                       (if-let [ancestrs (ancestors k)]
                         (str "-> "(clojure.string/join "," ancestrs))
                         "")
                       (let [attr-map (get attributes k)]
                         (if (seq attr-map)
                           (pr-str (:core.component/fn-params attr-map))
                           #_(binding [*print-level* nil]
                               (with-out-str
                                (clojure.pprint/pprint attr-map)))
                           "")))
              #_(doseq [system-name (component-systems k)]
                  (println "  * " system-name)))))))

 ; & add all tx's ?

 ; -> only components who have a system ???
 ; -> not skill/cost or something ...
 ; and each system just add docstring
 ; and component schema
 ; then expandable and upload to wiki

 ; https://gist.github.com/pierrejoubert73/902cc94d79424356a8d20be2b382e1ab
 ; https://docs.github.com/en/get-started/writing-on-github/working-with-advanced-formatting/organizing-information-with-collapsed-sections

 ; -> and after each 'build' I can have a bash script which uploads the components go github
 )

(defn- k->component-ns [k]
  (symbol (str "components." (name (namespace k)) "." (name k))))

(def warn-name-ns-mismatch? false)

(defmacro defcomponent [k attr-map & sys-impls]
  (let [let-bindings? (not (list? (first sys-impls)))
        let-bindings (if let-bindings?
                       (first sys-impls)
                       nil)
        sys-impls (if let-bindings?
                    (rest sys-impls)
                    sys-impls)]
    `(do

      (assert (keyword? ~k) (pr-str ~k))

      (when (and warn-name-ns-mismatch?
                 (not= (#'k->component-ns ~k) (ns-name *ns*)))
        (println "WARNING: defcomponent " ~k " is not matching with namespace name " (ns-name *ns*)))

      (when (and warn-on-override (get attributes ~k))
        (println "WARNING: Overwriting defcomponent" ~k))

      (alter-var-root #'attributes assoc ~k ~attr-map)

      ~@(for [[sys & fn-body] sys-impls
              :let [sys-var (resolve sys)
                    sys-params (:params (meta sys-var))
                    fn-params (first fn-body)
                    fn-exprs (rest fn-body)]]
          (do

           ; TODO throw stuff in separate functions

           (when-not sys-var
             (throw (IllegalArgumentException. (str sys " does not exist."))))

           (when-not (= (count sys-params) (count fn-params)) ; defmethods do not check this, that's why we check it here.
             (throw (IllegalArgumentException.
                     (str sys-var " requires " (count sys-params) " args: " sys-params "."
                          " Given " (count fn-params)  " args: " fn-params))))

           `(do
             (assert (keyword? ~k) (pr-str ~k))

             (alter-var-root #'attributes assoc-in [~k :core.component/fn-params ~(name (symbol sys-var))] (quote ~(first fn-params)))

             (when (and warn-on-override
                        (get (methods @~sys-var) ~k))
               (println "WARNING: Overwriting defcomponent" ~k "on" ~sys-var))

             (defmethod ~sys ~k ~(symbol (str (name (symbol sys-var)) "." (name k)))
               [& params#]
               (let [~(if let-bindings? let-bindings '_) (get (first params#) 1) ; get because maybe component is just [:foo] without v.
                     ~fn-params params#]
                 ~@fn-exprs)))))
      ~k)))


(defsystem create [_ ctx])
(defmethod create :default [[_ v] _ctx]
  v)

(defsystem destroy [_])
(defmethod destroy :default [_])

(defsystem info-text [_ ctx])
(defmethod info-text :default [_ ctx])

(defn create-all [components ctx]
  (assert (map? ctx))
  (reduce (fn [m [k v]]
            (assoc m k (create [k v] ctx)))
          {}
          components))

(defn- ks->components [ks]
  (zipmap ks (repeat nil)))

(defn ks->create-all [ks ctx]
  (create-all (ks->components ks) ctx))

(defn create-into [ctx components]
  (assert (map? ctx))
  (reduce (fn [ctx [k v]]
            (if-let [v (create [k v] ctx)]
              (assoc ctx k v)
              ctx))
          ctx
          components))

; ??
; tools namespace doesn't load them in right order, if we are asserting order of component matters, we need to reload them.
; but this is quite though because then so much loading
; or those which just define components which are used somewhere else we might pass a :reload flag there ???
; TODO force reload fucks up other stuff !!!  ???
; this has to work ... shit !

(defn load! [components & {:keys [log?]}]
  (assert (apply distinct? (map first components)))
  (doseq [[k _] components
          :let [component-ns (k->component-ns k)]]
    (when log? (println "require " component-ns))
    (require component-ns)
    (assert (find-ns component-ns)
            (str "Cannot find component namespace " component-ns))))

(defn load-ks! [ks]
  (load! (ks->components ks)))
