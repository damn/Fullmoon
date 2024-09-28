(in-ns 'clojure.ctx)

(defsystem ->value "..." [_])

(defn def-attributes [& attributes-data]
  {:pre [(even? (count attributes-data))]}
  (doseq [[k data] (partition 2 attributes-data)]
    (defcomponent* k {:data data})))

(defn def-type [k {:keys [schema overview]}]
  (defcomponent k
    {:data [:map (conj schema :property/id)]
     :overview overview}))

(defn- data-component [k]
  (try (let [data (:data (safe-get component-attributes k))]
         (if (vector? data)
           [(first data) (->value data)]
           [data (safe-get component-attributes data)]))
       (catch Throwable t
         (throw (ex-info "" {:k k} t)))))
