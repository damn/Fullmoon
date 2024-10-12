(ns core.data)

; probably a better name for this
; :d/type ?
; or document where its used as param or name

(defn ->type [data]
  (if (vector? data)
    (data 0)
    data))

(defmulti edn->value (fn [data v] (->type data)))
(defmethod edn->value :default [_data v] v)

(defmulti schema ->type)
(defmethod schema :default [data] data)

(defmethod schema :number  [_] number?)
(defmethod schema :nat-int [_] nat-int?)
(defmethod schema :int     [_] int?)
(defmethod schema :pos     [_] pos?)
(defmethod schema :pos-int [_] pos-int?)

(defmethod schema :enum [[_ items]]
  (apply vector :enum items))
