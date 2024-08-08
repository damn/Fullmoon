(ns api.effect
  (:require [clojure.string :as str]
            [core.component :refer [defsystem]]))

(defsystem text          [_])
(defmethod text :default [_])

(defsystem valid-params? [_])
(defmethod valid-params? :default [_] true)

(defsystem useful?       [_ ctx]) ; only used @ AI ??
(defmethod useful? :default [_ _ctx] true)

(defsystem render-info   [_ g])
(defmethod render-info :default [_ _g])
