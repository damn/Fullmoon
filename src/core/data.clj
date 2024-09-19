(ns core.data
  (:require [utils.core :refer [safe-get]]
            [core.component :refer [defsystem defcomponent*] :as component]))

(defsystem ->value "Returns the data value. Required system, no default." [_])

(defn component [k]
  (try (let [data (:data (safe-get component/attributes k))]
         (if (vector? data)
           [(first data) (->value data)]
           [data (safe-get component/attributes data)]))
       (catch Throwable t
         (throw (ex-info "" {:k k} t)))))

(defmulti edn->value (fn [data v ctx] (if data (data 0))))
(defmethod edn->value :default [_data v _ctx]
  v)

(defn- data->widget [[k v]]
  (or (:widget v) k))

(defmulti ->widget      (fn [data _v _ctx] (data->widget data)))
(defmulti widget->value (fn [data _widget] (data->widget data)))

(defn def-attributes [& attributes-data]
  {:pre [(even? (count attributes-data))]}
  (doseq [[k data] (partition 2 attributes-data)]
    (defcomponent* k {:data data})))
