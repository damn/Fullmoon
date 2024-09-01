(ns components.data.core
  (:require [core.component :as component :refer [defcomponent]]
            [core.context :as ctx]
            core.animation
            core.image))

(defcomponent :boolean {:schema :boolean :default-value true})
(defcomponent :some    {:schema :some})
(defcomponent :string  {:schema :string})
(defcomponent :sound   {:schema :string})

(defcomponent :image
  {:schema :some
   :value->edn core.image/image->edn
   :edn->value core.image/edn->image})

(defcomponent :animation
  {:schema :some
   :value->edn core.animation/animation->edn
   :edn->value core.animation/edn->animation})

(defcomponent :number  {:schema number?})
(defcomponent :nat-int {:schema nat-int? :widget :number})
(defcomponent :int     {:schema int?     :widget :number})
(defcomponent :pos     {:schema pos?     :widget :number})
(defcomponent :pos-int {:schema pos-int? :widget :number})

(defcomponent :enum
  (component/->data [[_ items]]
    {:schema (apply vector :enum items)
     :items items}))

; TODO schema not checking if exists
(defcomponent :one-to-many
  (component/->data [[_ property-type]]
    {:schema [:set [:qualified-keyword]]
     :linked-property-type property-type
     :fetch-references map
     :value->edn (fn [properties]
                   (set (map :property/id properties)))}))

; TODO schema not checking if exists
(defcomponent :one-to-one
  (component/->data [[_ property-type]]
    {:schema [:qualified-keyword]
     :linked-property-type property-type
     :fetch-references get
     :value->edn :property/id}))

(defcomponent :qualified-keyword
  (component/->data [schema]
    {:schema schema}))

(defn- map-schema [ks]
  (apply vector :map {:closed true} (component/attribute-schema ks)))

(defcomponent :map
  (component/->data [[_ ks]]
    {:schema (map-schema ks)
     :default-value (zipmap ks (map component/default-value ks))}))

(defcomponent :components
  (component/->data [[_ ks]]
    {:widget :map
     :schema (map-schema ks)
     :components ks
     :default-value {}}))

(defcomponent :components-ns
  (component/->data [[_ k]]
    (let [ks (filter #(= (name k) (namespace %)) (keys component/attributes))]
      (component/->data [:components ks]))))
