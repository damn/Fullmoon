(ns core.component
  (:require [clojure.gdx.utils :refer [safe-get]]
            [core.data :as data]))

(def systems "Map of all systems as key of name-string to var." {})

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
    (alter-var-root #'systems assoc ~(str (ns-name *ns*) "/" sys-name) (var ~sys-name))
    (var ~sys-name)))

(def attributes {})

(defn defc*
  "Defines a component without systems methods, so only to set metadata."
  [k attr-map]
  (when (get attributes k)
    (println "WARNING: Overwriting defc" k "attr-map"))
  (alter-var-root #'attributes assoc k attr-map))

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
             (alter-var-root #'attributes assoc-in [~k :params ~(name (symbol sys-var))] (quote ~fn-params))
             (when (get (methods @~sys-var) ~k)
               (println "WARNING: Overwriting defc" ~k "on" ~sys-var))
             (defmethod ~sys ~k ~(symbol (str (name (symbol sys-var)) "." (name k)))
               [& params#]
               (let [~(if let-bindings let-bindings '_) (get (first params#) 1) ; get because maybe component is just [:foo] without v.
                     ~fn-params params#]
                 ~@fn-exprs)))))
      ~k)))

(defn data-type [k]
  (try (data/->type (:data (safe-get attributes k)))
       (catch Throwable t
         (throw (ex-info "" {:k k} t)))))

(defn data-schema [k] (:schema (data-type k)))
