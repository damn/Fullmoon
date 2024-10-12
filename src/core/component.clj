(ns core.component
  "A component is a vector of `[k & values?]`.
  For example a minimal component is `[:foo]`"
  (:require [clojure.gdx.utils :refer [safe-get index-of]]
            [clojure.string :as str]
            [core.data :as data]))

(def systems "Map of all systems as key of name-string to var." {})

(defmacro defsystem
  "A system is a multimethod which takes as first argument a component and dispatches on k."
  ([sys-name]
   `(defsystem ~sys-name nil ['_]))

  ([sys-name params-or-doc]
   (let [[doc params] (if (string? params-or-doc)
                        [params-or-doc ['_]]
                        [nil params-or-doc])]
     `(defsystem ~sys-name ~doc ~params)))

  ([sys-name docstring params]
   (when (zero? (count params))
     (throw (IllegalArgumentException. "First argument needs to be component.")))
   (when-let [avar (resolve sys-name)]
     (println "WARNING: Overwriting defsystem:" avar))
   `(do
     (defmulti ~(vary-meta sys-name assoc :params (list 'quote params))
       ~(str "[[defsystem]] with params: `" params (when docstring "` \n\n " docstring))
       (fn [[k#] & _args#] k#))
     (alter-var-root #'systems assoc ~(str (ns-name *ns*) "/" sys-name) (var ~sys-name))
     (var ~sys-name))))

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
(defsystem foo)

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

(defn data [k]
  (:data (safe-get attributes k)))

(defn- attribute-schema
  "Can define keys as just keywords or with schema-props like [:foo {:optional true}]."
  [ks]
  (for [k ks
        :let [k? (keyword? k)
              schema-props (if k? nil (k 1))
              k (if k? k (k 0))]]
    (do
     (assert (keyword? k))
     (assert (or (nil? schema-props) (map? schema-props)) (pr-str ks))
     [k schema-props (data/schema (data k))])))

(defn- map-schema [ks]
  (apply vector :map {:closed true} (attribute-schema ks)))

(defmethod data/schema :map [[_ ks]]
  (map-schema ks))

(defmethod data/schema :map-optional [[_ ks]]
  (map-schema (map (fn [k] [k {:optional true}]) ks)))

(defn- namespaced-ks [ns-name-k]
  (filter #(= (name ns-name-k) (namespace %))
          (keys attributes)))

(defmethod data/schema :components-ns [[_ ns-name-k]]
  (data/schema [:map-optional (namespaced-ks ns-name-k)]))

(def ^:private info-text-k-order [:property/pretty-name
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

(defsystem info "Return info-string (for tooltips,etc.). Default nil.")
(defmethod info :default [_])

(declare ^:dynamic *info-text-entity*)

(defn info-text
  "Recursively generates info-text via [[core.component/info]]."
  [components]
  (->> components
       sort-k-order
       (keep (fn [{v 1 :as component}]
               (str (try (binding [*info-text-entity* components]
                           (info component))
                         (catch Throwable t
                           ; calling from property-editor where entity components
                           ; have a different data schema than after ->mk
                           ; and info-text might break
                           (pr-str component)))
                    (when (map? v)
                      (str "\n" (info-text v))))))
       (str/join "\n")
       remove-newlines))
