(ns core.component)

; TODO line number for overwrite warnings or ns at least....
(def warn-on-override? true)

(def defsystems {})

(defmacro defsystem [sys-name params]
  (when (zero? (count params))
    (throw (IllegalArgumentException. "First argument needs to be component.")))
  (when warn-on-override?
    (when-let [avar (resolve sys-name)]
      (println "WARNING: Overwriting defsystem:" avar)))
  `(do
    (defmulti ~(vary-meta sys-name assoc :params (list 'quote params))
      (fn ~(symbol (str (name sys-name))) [& args#]
        (ffirst args#)))
    (alter-var-root #'defsystems assoc ~(str (ns-name *ns*) "/" sys-name) (var ~sys-name))
    (var ~sys-name)))

(def attributes {}) ; call 'components'?

(def warn-name-ns-mismatch? false)

(defn- k->component-ns [k]
  (symbol (str "components." (name (namespace k)) "." (name k))))

(defn check-warn-ns-name-mismatch [k]
  (when (and warn-name-ns-mismatch?
             (namespace k)
             (not= (k->component-ns k) (ns-name *ns*)))
    (println "WARNING: defcomponent " k " is not matching with namespace name " (ns-name *ns*))))

(defn defcomponent* [k attr-map & {:keys [warn-on-override?]}]
  (when (and warn-on-override? (get attributes k))
    (println "WARNING: Overwriting defcomponent" k "attr-map"))
  (alter-var-root #'attributes assoc k attr-map))

(defmacro defcomponent [k & sys-impls]
  (check-warn-ns-name-mismatch k)
  (let [attr-map? (not (list? (first sys-impls)))
        attr-map  (if attr-map? (first sys-impls) {})
        sys-impls (if attr-map? (rest sys-impls) sys-impls)
        let-bindings (:let attr-map)
        attr-map (dissoc attr-map :let)]
    `(do
      (when ~attr-map?
        (defcomponent* ~k ~attr-map) :warn-on-override? warn-on-override?)

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

             (alter-var-root #'attributes assoc-in [~k :params ~(name (symbol sys-var))] (quote ~fn-params))

             (when (and warn-on-override?
                        (get (methods @~sys-var) ~k))
               (println "WARNING: Overwriting defcomponent" ~k "on" ~sys-var))

             (defmethod ~sys ~k ~(symbol (str (name (symbol sys-var)) "." (name k)))
               [& params#]
               (let [~(if let-bindings let-bindings '_) (get (first params#) 1) ; get because maybe component is just [:foo] without v.
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

;; TX

; 1. return new ctx if we change something in the ctx or have side effect -> will be recorded
; when returning a 'map?'

; 2. return seq of txs -> those txs will be done recursively
; 2.1 also seq of fns wih [ctx] param can be passed.

; 3. return nil in case of doing nothing -> will just continue with existing ctx.

; do NOT do a ctx/do! inside a effect/do! because then we have to return a context
; and that means that transaction will be recorded and done double with all the sub-transactions
; in the replay mode
; we only want to record actual side effects, not transactions returning other lower level transactions
(defsystem do! [_ ctx])

;; Effect

(defsystem applicable? [_ ctx])

(defsystem useful? [_ ctx])
(defmethod useful? :default [_ ctx] true)

(defsystem render [_ g ctx])
(defmethod render :default [_ g ctx])

;;

(defn apply-system [components system & args]
  (reduce (fn [m [k v]]
            (assoc m k (apply system [k v] args)))
          {}
          components))

(defn create-all [components ctx]
  (assert (map? ctx))
  (apply-system components create ctx))

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

(defn doc [k]
  (:doc (get attributes k)))
