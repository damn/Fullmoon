(ns components.data.core
  (:require [core.component :as component :refer [defcomponent]]
            [core.context :as ctx]
            core.animation
            core.image))

(defcomponent :boolean   {:widget :check-box  :schema :boolean :default-value true})
(defcomponent :some      {:widget :label      :schema :some})
(defcomponent :string    {:widget :text-field :schema :string})
(defcomponent :sound     {:widget :sound      :schema :string})

(defcomponent :image
  {:widget :image
   :schema :some
   :->edn core.image/image->edn
   :->value core.image/edn->image})

(defcomponent :animation
  {:widget :animation
   :schema :some
   :->edn core.animation/animation->edn
   :->value core.animation/edn->animation})

(defcomponent :number    {:widget :text-field :schema number?})
(defcomponent :nat-int   {:widget :text-field :schema nat-int?})
(defcomponent :int       {:widget :text-field :schema int?})
(defcomponent :pos       {:widget :text-field :schema pos?})
(defcomponent :pos-int   {:widget :text-field :schema pos-int?})

(defcomponent :enum
  (component/->data [[_ items]]
    {:widget :enum
     :schema (apply vector :enum items)
     :items items}))

(defcomponent :one-to-many-ids
  (component/->data [[_ property-type]]
    {:widget :one-to-many
     :schema [:set :qualified-keyword]
     :linked-property-type property-type
     :fetch-references (fn [ids ctx]
                         (map #(ctx/property ctx %) ids))}))

(defcomponent :qualified-keyword
  (component/->data [schema]
    {:widget :label
     :schema schema}))

(defn- map-schema [ks]
  (apply vector :map {:closed true} (component/attribute-schema ks)))

(defcomponent :map
  (component/->data [[_ ks]]
    {:widget :nested-map
     :schema (map-schema ks)
     :default-value (zipmap ks (map component/default-value ks))}))

(defcomponent :components
  (component/->data [[_ ks]]
    {:widget :nested-map
     :schema (map-schema ks)
     :components ks
     :default-value {}}))

(defcomponent :components-ns
  (component/->data [[_ k]]
    (let [ks (filter #(= (name k) (namespace %)) (keys component/attributes))]
      (component/->data [:components ks]))))
