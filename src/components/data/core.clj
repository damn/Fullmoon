(ns components.data.core
  (:require [core.component :as component :refer [defcomponent]]))

(defcomponent :boolean   {:widget :check-box  :schema :boolean :default-value true})
(defcomponent :some      {:widget :label      :schema :some})
(defcomponent :string    {:widget :text-field :schema :string})
(defcomponent :sound     {:widget :sound      :schema :string})
(defcomponent :image     {:widget :image      :schema :some})
(defcomponent :animation {:widget :animation  :schema :some})
(defcomponent :number    {:widget :text-field :schema number?})
(defcomponent :nat-int   {:widget :text-field :schema nat-int?})
(defcomponent :int       {:widget :text-field :schema int?})
(defcomponent :pos       {:widget :text-field :schema pos?})
(defcomponent :pos-int   {:widget :text-field :schema pos-int?})

(defcomponent :enum
  (component/->data [[_ & items :as schema]]
    {:widget :enum
     :schema schema
     :items items}))

; not checking if one of existing ids used
; widget requires property/image for displaying overview
(defcomponent :one-to-many-ids
  (component/->data [[_ property-type]]
    {:widget :one-to-many
     :schema [:set :qualified-keyword] ; namespace missing
     :linked-property-type property-type})) ; => fetch from schema namespaced ?

(defcomponent :qualified-keyword
  (component/->data [schema]
    {:widget :label
     :schema schema}))

(defn- map-schema [ks]
  (apply vector :map {:closed true} (component/attribute-schema ks)))

(defcomponent :map
  (component/->data [[_ & ks]]
    {:widget :nested-map
     :schema (map-schema ks)
     :default-value (zipmap ks (map component/default-value ks))}))

(defcomponent :components
  (component/->data [[_ & ks]]
    {:widget :nested-map
     :schema (map-schema ks)
     :components ks
     :default-value {}}))

(defcomponent :components-ns
  (component/->data [[_ k]]
    (let [ks (filter #(= (name k) (namespace %)) (keys component/attributes))]
      (component/->data (apply vector :components ks)))))
