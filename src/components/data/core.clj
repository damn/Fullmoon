(ns components.data.core
  (:require [clojure.edn :as edn]
            [malli.core :as m]
            [utils.core :as utils]
            [core.component :as component :refer [defcomponent]]
            [core.context :as ctx]
            [core.data :as data]
            [gdx.scene2d.actor :as actor]
            [gdx.scene2d.ui.text-field :as text-field])
  (:import (com.kotcrab.vis.ui.widget VisCheckBox VisSelectBox)))

(defcomponent :some {:schema :some})
(defcomponent :boolean {:schema :boolean})

(defmethod data/->widget :boolean [_ checked? ctx]
  (assert (boolean? checked?))
  (ctx/->check-box ctx "" (fn [_]) checked?))

(defmethod data/widget->value :boolean [_ widget]
  (.isChecked ^VisCheckBox widget))

(defcomponent :string {:schema :string})

(defn- add-schema-tooltip! [widget data]
  (actor/add-tooltip! widget (str "Schema: " (pr-str (m/form (:schema data)))))
  widget)

(defmethod data/->widget :string [[_ data] v ctx]
  (add-schema-tooltip! (ctx/->text-field ctx v {})
                       data))

(defmethod data/widget->value :string [_ widget]
  (text-field/text widget))

(defcomponent :number  {:schema number?})
(defcomponent :nat-int {:schema nat-int? :widget :number})
(defcomponent :int     {:schema int?     :widget :number})
(defcomponent :pos     {:schema pos?     :widget :number})
(defcomponent :pos-int {:schema pos-int? :widget :number})

(defmethod data/->widget :number [[_ data] v ctx]
  (add-schema-tooltip! (ctx/->text-field ctx (utils/->edn-str v) {})
                       data))

(defmethod data/widget->value :number [_ widget]
  (edn/read-string (text-field/text widget)))

(defcomponent :enum
  (data/->value [[_ items]]
    {:schema (apply vector :enum items)}))

(defmethod data/->widget :enum [[_ data] v ctx]
  (ctx/->select-box ctx {:items (map utils/->edn-str (rest (:schema data)))
                         :selected (utils/->edn-str v)}))

(defmethod data/widget->value :enum [_ widget]
  (edn/read-string (.getSelected ^VisSelectBox widget)))

(defcomponent :qualified-keyword
  (data/->value [schema]
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
     [k properties (:schema ((data/component k) 1))])))

(defn- map-schema [ks]
  (apply vector :map {:closed true} (attribute-schema ks)))

(defcomponent :map
  (data/->value [[_ ks]]
    {:schema (map-schema ks)}))

(defcomponent :map-optional
  (data/->value [[_ ks]]
    {:widget :map
     :schema (map-schema (map (fn [k] [k {:optional true}]) ks))}))

(defn- namespaced-ks [ns-name-k]
  (filter #(= (name ns-name-k) (namespace %))
          (keys component/attributes)))

(defcomponent :components-ns
  (data/->value [[_ ns-name-k]]
    (data/->value [:map-optional (namespaced-ks ns-name-k)])))
