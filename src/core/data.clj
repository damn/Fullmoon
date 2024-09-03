(ns core.data
  (:require [utils.core :as utils]
            [core.context :as ctx]
            [core.scene2d.actor :as actor]))

(defn- data->widget [[k v]]
  (or (:widget v) k))

(defmulti ->widget      (fn [data _v _ctx] (data->widget data)))
(defmulti widget->value (fn [data _widget] (data->widget data)))

(defmethod ->widget :default [_ v ctx]
  (ctx/->label ctx (utils/->edn-str v)))

(defmethod widget->value :default [_ widget]
  (actor/id widget))
