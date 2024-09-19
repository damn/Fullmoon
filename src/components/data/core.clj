(ns components.data.core
  (:require [clojure.edn :as edn]
            [malli.core :as m]
            [utils.core :as utils]
            [core.component :as component :refer [defcomponent]]
            [core.data :as data]
            [gdx.scene2d.actor :as actor]
            [gdx.scene2d.ui :as ui])
  (:import (com.kotcrab.vis.ui.widget VisCheckBox VisSelectBox VisTextField)))

(defcomponent :some {:schema :some})
(defcomponent :boolean {:schema :boolean})

(defmethod data/->widget :boolean [_ checked? _ctx]
  (assert (boolean? checked?))
  (ui/->check-box "" (fn [_]) checked?))

(defmethod data/widget->value :boolean [_ widget]
  (.isChecked ^VisCheckBox widget))

(defcomponent :string {:schema :string})

(defn- add-schema-tooltip! [widget data]
  (actor/add-tooltip! widget (str "Schema: " (pr-str (m/form (:schema data)))))
  widget)

(defmethod data/->widget :string [[_ data] v _ctx]
  (add-schema-tooltip! (ui/->text-field v {})
                       data))

(defmethod data/widget->value :string [_ widget]
  (.getText ^VisTextField widget))

(defcomponent :number  {:schema number?})
(defcomponent :nat-int {:schema nat-int?})
(defcomponent :int     {:schema int?})
(defcomponent :pos     {:schema pos?})
(defcomponent :pos-int {:schema pos-int?})

(defmethod data/->widget :number [[_ data] v _ctx]
  (add-schema-tooltip! (ui/->text-field (utils/->edn-str v) {})
                       data))

(defmethod data/widget->value :number [_ widget]
  (edn/read-string (.getText ^VisTextField widget)))

(defcomponent :enum
  (data/->value [[_ items]]
    {:schema (apply vector :enum items)}))

(defmethod data/->widget :enum [[_ data] v _ctx]
  (ui/->select-box {:items (map utils/->edn-str (rest (:schema data)))
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
    {:schema (map-schema (map (fn [k] [k {:optional true}]) ks))}))

(defn- namespaced-ks [ns-name-k]
  (filter #(= (name ns-name-k) (namespace %))
          (keys component/attributes)))

(defcomponent :components-ns
  (data/->value [[_ ns-name-k]]
    (data/->value [:map-optional (namespaced-ks ns-name-k)])))
