(ns cdq.attributes
  (:require [malli.core :as m]
            core.component
            [data.val-max :refer [val-max-schema]]))

; this is 'value-types'
; and each value-type can actually define modifiers&effects for it
; e.g. mana/hp val-max-data -> create there automatically

; TODO validate value-type-schema
; => do @ specific component types, write extra macro
; context/def or entity/def etc.
;(assert (:schema attr-map) k) (not all this, ...)
;(assert (:widget attr-map) k)
; optional: :doc ! (maybe not optional make ...)

(def sound        {:widget :sound      :schema :string})
(def image        {:widget :image      :schema :some})
(def animation    {:widget :animation  :schema :some})
(def string-attr  {:widget :text-field :schema :string})
(def boolean-attr {:widget :check-box  :schema :boolean :default-value true})
(def val-max-attr {:widget :text-field :schema (m/form val-max-schema)})
(def nat-int-attr {:widget :text-field :schema nat-int?})
(def pos-attr     {:widget :text-field :schema pos?})
(def pos-int-attr {:widget :text-field :schema pos-int?})

(defn enum [& items]
  {:widget :enum
   :schema (apply vector :enum items)
   :items items})

; TODO not checking if one of existing ids used
; widget requires property/image.
(defn one-to-many-ids [property-type]
  {:widget :one-to-many
   :schema [:set :qualified-keyword]
   :linked-property-type property-type}) ; => fetch from schema namespaced ?

(defn map-attribute [& attr-ks] ; TODO similar to components-attribute
  {:widget :nested-map
   :schema (vec (concat [:map {:closed true}]
                        (for [k attr-ks]
                          (vector k (or (:schema (get core.component/attributes k)) :some)))))})

(defn components-attribute [component-namespace]
  (let [component-attributes (filter #(= (name component-namespace) (namespace %))
                                     (keys core.component/attributes))]
    {:widget :nested-map
     :schema (vec (concat [:map {:closed true}]
                          (for [k component-attributes]
                            [k {:optional true} (or (:schema (get core.component/attributes k)) :some)])))
     :components component-attributes})) ; => fetch from schema ? (optional? )
