(ns core.property.data.core
  (:require [clojure.edn :as edn]
            [malli.core :as m]
            [core.utils.core :as utils]
            [core.component :as component :refer [defcomponent]]
            [core.ctx.property :as property]
            [core.ui.actor :as actor]
            [core.ui :as ui])
  (:import (com.kotcrab.vis.ui.widget VisCheckBox VisSelectBox VisTextField)))

(defcomponent :some {:schema :some})
(defcomponent :boolean {:schema :boolean})

(defmethod property/->widget :boolean [_ checked? _ctx]
  (assert (boolean? checked?))
  (ui/->check-box "" (fn [_]) checked?))

(defmethod property/widget->value :boolean [_ widget]
  (.isChecked ^VisCheckBox widget))

(defcomponent :string {:schema :string})

(defn- add-schema-tooltip! [widget data]
  (actor/add-tooltip! widget (str "Schema: " (pr-str (m/form (:schema data)))))
  widget)

(defmethod property/->widget :string [[_ data] v _ctx]
  (add-schema-tooltip! (ui/->text-field v {})
                       data))

(defmethod property/widget->value :string [_ widget]
  (.getText ^VisTextField widget))

(defcomponent :number  {:schema number?})
(defcomponent :nat-int {:schema nat-int?})
(defcomponent :int     {:schema int?})
(defcomponent :pos     {:schema pos?})
(defcomponent :pos-int {:schema pos-int?})

(defmethod property/->widget :number [[_ data] v _ctx]
  (add-schema-tooltip! (ui/->text-field (utils/->edn-str v) {})
                       data))

(defmethod property/widget->value :number [_ widget]
  (edn/read-string (.getText ^VisTextField widget)))

(defcomponent :enum
  (property/->value [[_ items]]
    {:schema (apply vector :enum items)}))

(defmethod property/->widget :enum [[_ data] v _ctx]
  (ui/->select-box {:items (map utils/->edn-str (rest (:schema data)))
                    :selected (utils/->edn-str v)}))

(defmethod property/widget->value :enum [_ widget]
  (edn/read-string (.getSelected ^VisSelectBox widget)))

(defcomponent :qualified-keyword
  (property/->value [schema]
    {:schema schema}))

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
     [k properties (:schema ((property/data-component k) 1))])))

(defn- map-schema [ks]
  (apply vector :map {:closed true} (attribute-schema ks)))

(defcomponent :map
  (property/->value [[_ ks]]
    {:schema (map-schema ks)}))

(defcomponent :map-optional
  (property/->value [[_ ks]]
    {:schema (map-schema (map (fn [k] [k {:optional true}]) ks))}))

(defn- namespaced-ks [ns-name-k]
  (filter #(= (name ns-name-k) (namespace %))
          (keys component/attributes)))

(defcomponent :components-ns
  (property/->value [[_ ns-name-k]]
    (property/->value [:map-optional (namespaced-ks ns-name-k)])))
