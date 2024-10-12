(ns core.data)

; probably a better name for this
; :d/type ?
; or document where its used as param or name

(defn ->type [data]
  (if (vector? data)
    (data 0)
    data))

; this defined @ editor for image/one-to-many ...
(defmulti edn->value (fn [data v] (->type data)))
(defmethod edn->value :default [_data v] v)

; if this is just static no need defmethod ?
; (data/def :d/number number?)
(defmulti schema ->type)
(defmethod schema :default [data] data)

(defmethod schema :number  [_] number?)
(defmethod schema :nat-int [_] nat-int?)
(defmethod schema :int     [_] int?)
(defmethod schema :pos     [_] pos?)
(defmethod schema :pos-int [_] pos-int?)

(defmethod schema :enum [[_ items]]
  (apply vector :enum items))

(defmethod schema :sound [_]
  :string)

(defmethod schema :image [_]
  [:map {:closed true}
   [:file :string]
   [:sub-image-bounds {:optional true} [:vector {:size 4} nat-int?]]])

(defmethod schema :data/animation [_]
  [:map {:closed true}
   [:frames :some]
   [:frame-duration pos?]
   [:looping? :boolean]])
