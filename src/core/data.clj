(ns core.data
  (:require [core.context :as ctx]))

(defmulti edn->value (fn [data v ctx] (if data (data 0))))
(defmethod edn->value :default [_data v _ctx]
  v)

(defn- data->widget [[k v]]
  (or (:widget v) k))

(defmulti ->widget      (fn [data _v _ctx] (data->widget data)))
(defmulti widget->value (fn [data _widget] (data->widget data)))

; TODO set to preferred width/height ??? why layouting not working properly?
; use a tree?
; make example with plain data
; FIXME this not here?
(defn ->scroll-pane-cell [ctx rows]
  (let [table (ctx/->table ctx {:rows rows
                                :cell-defaults {:pad 1}
                                :pack? true})
        scroll-pane (ctx/->scroll-pane ctx table)]
    {:actor scroll-pane
     :width (- (ctx/gui-viewport-width ctx) 600) ;(+ (actor/width table) 200)
     :height (- (ctx/gui-viewport-height ctx) 100)})) ;(min (- (ctx/gui-viewport-height ctx) 50) (actor/height table))
