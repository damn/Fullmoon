(ns editor.widget
  (:require [component.schema :as schema]))

(comment
 ; Possible schemas:
 (keys (methods ->widget))
 ; plus in widget-type
 )

(defn- widget-type [schema _]
  (let [stype (schema/type schema)]
    (cond
     (#{:s/map-optional :s/components-ns} stype)
     :s/map

     (#{number? nat-int? int? pos? pos-int? :s/val-max} stype)
     number?

     :else stype)))

(defmulti create widget-type)
(defmulti value  widget-type)
