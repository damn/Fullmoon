(ns components.data.core
  (:require [malli.core :as m]
            [core.component :as component :refer [defcomponent]]
            [core.data :as data]
            [core.val-max :refer [val-max-schema]]))

(defcomponent :boolean   {:widget :check-box  :schema :boolean :default-value true})
(defcomponent :some      {:widget :label      :schema :some})
(defcomponent :string    {:widget :text-field :schema :string})
(defcomponent :sound     {:widget :sound      :schema :string})
(defcomponent :image     {:widget :image      :schema :some})
(defcomponent :animation {:widget :animation  :schema :some})
(defcomponent :val-max   {:widget :text-field :schema (m/form val-max-schema)})
(defcomponent :number    {:widget :text-field :schema number?})
(defcomponent :nat-int   {:widget :text-field :schema nat-int?})
(defcomponent :int       {:widget :text-field :schema int?})
(defcomponent :pos       {:widget :text-field :schema pos?})
(defcomponent :pos-int   {:widget :text-field :schema pos-int?})

(defcomponent :enum
  (component/data [[k & items :as schema]]
    {:widget :enum
     :schema schema
     :items items}))

; not checking if one of existing ids used
; widget requires property/image for displaying overview
(defcomponent :one-to-many-ids
  (component/data [[_ property-type]]
    {:widget :one-to-many
     :schema [:set :qualified-keyword] ; namespace missing
     :linked-property-type property-type})) ; => fetch from schema namespaced ?

(defcomponent :qualified-keyword
  (component/data [schema]
    {:widget :label
     :schema schema}))

(defcomponent :map
  (component/data [[_ & ks]]
    {:widget :nested-map
     :schema (data/map-schema ks)}))

(defcomponent :components
  (component/data [[_ & ks]]
    {:widget :nested-map
     :schema (data/map-schema ks)
     :components ks}))

(defcomponent :components-ns
  (component/data [[_ k]]
    (let [ks (filter #(= (name k) (namespace %)) (keys component/attributes))]
      {:widget :nested-map
       :schema (data/map-schema ks)
       :components ks})))
