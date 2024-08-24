; this is actually editor/components ... -> and maybe make into a function
; for components for loading dependencies doesn't matter
(ns core.data
  (:require [malli.core :as m]
            [core.component :as component]
            [utils.core :as utils]
            [core.val-max :refer [val-max-schema]]))

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
                          (vector k (or (:schema (get component/attributes k))
                                        :some)))))})

(defn optional? [attr-m]
  (let [optional? (get attr-m :optional? :not-found)]
    (if (= optional? :not-found)
      true
      optional?)))

(defn components [ks]
  {:widget :nested-map
   :schema (vec (concat [:map {:closed true}]
                        (for [k ks
                              :let [attr-m (get component/attributes k)]]
                          [k {:optional (optional? attr-m)} (or (:schema attr-m) :some)])))
   :components ks})

(defn components-attribute [component-namespace]
  (components (filter #(= (name component-namespace) (namespace %))
                      (keys component/attributes))))

; TODO similar to map-attribute & components-attribute
(defn map-attribute-schema [id-attribute attr-ks]
  (m/schema
   (vec (concat [:map {:closed true} id-attribute] ; TODO same id-attribute w. different namespaces ...
                ; creature/id ?
                ; item/id ?
                (for [k attr-ks]
                  (vector k (:schema (utils/safe-get component/attributes k))))))))
