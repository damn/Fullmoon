(ns core.data
  (:require [utils.core :refer [safe-get]]
            [core.component :refer [defsystem defcomponent*] :as component]))

(defsystem ->value "..." [_])

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

(defn- k->widget [k]
  (cond
   (#{:map-optional :components-ns} k) :map
   (#{:number :nat-int :int :pos :pos-int :val-max} k) :number
   :else k))

(defmulti ->widget      (fn [[k _] _v _ctx] (k->widget k)))
(defmulti widget->value (fn [[k _] _widget] (k->widget k)))

(defn def-attributes [& attributes-data]
  {:pre [(even? (count attributes-data))]}
  (doseq [[k data] (partition 2 attributes-data)]
    (defcomponent* k {:data data})))
