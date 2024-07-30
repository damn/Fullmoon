(ns cdq.api.effect
  (:require [core.component :as component]))

(component/defn text          [_ ctx])
(defmethod text :default [_ _])

(component/defn valid-params? [_ ctx])
(defmethod valid-params? :default [_ _] true)

(component/defn useful?       [_ ctx])
(defmethod useful? :default [_ _] true)

(component/defn render-info   [_ g ctx])
(defmethod render-info :default [_ _g _ctx])
