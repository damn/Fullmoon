(ns core.data
  (:require [malli.core :as m]
            [utils.core :refer [safe-get]]
            [core.component :as component :refer [defcomponent]]
            [core.val-max :refer [val-max-schema]]))

; Next, see :context/properties (dependencies on core.data check)
; can move defcomponents into app.edn itself

; also defstats -> effects & operations should be deducted from :schema
; and stats itself can be passed @ created in resources/app.edn

; * which data is there? (for properties - or in general)
; * do they all have proper widget/schema/optional? - optional? belongs in the map definition not attribute ?
; * define not at entity/foo but at components/properties ? (creature state different than entity/state ..)

; => just pass :schema/:optional?/:doc
; from schema calculate :widget (my use cases first)
; remove core.data dependencies

; TODO be clear (just use namespaced keywords for all these things ...)
; * my core-schema mostly == malli schema, adjust malli registry/etc. for components/image/sound/...
;   and just use functions to get e.g. items out of enum
; https://github.com/metosin/malli?tab=readme-ov-file#qualified-keys-in-a-map

; * (m/form entity/movement-speed-schema)
; * target-all/target-entity not an effect || each skill/effect requires only a target
;    (one of multiple choices) like in map generators

; TODO :derive add to defcomponent map ? (used at val-max)

; TODO validate value-type-schema / component schema map?
; => do @ specific component types, write extra macro
; context/def or entity/def etc.
;(assert (:schema attr-map) k) (not all this, ...)
;(assert (:widget attr-map) k)
; optional: :doc ! (maybe not optional make ...)

(defn- component-k->data-type [ck]
  (try
   (let [core-schema (:schema (safe-get component/attributes ck))
         data-type (safe-get component/attributes
                             (if (vector? core-schema)
                               (first core-schema)
                               core-schema))]
     (if (seq data-type)
       data-type
       (component/data core-schema)))
   (catch Throwable t
     (throw (ex-info "" {:ck ck} t)))))

(defn ck->widget                [ck] (:widget               (component-k->data-type ck)))
(defn ck->schema                [ck] (:schema               (component-k->data-type ck)))
(defn ck->enum-items            [ck] (:items                (component-k->data-type ck)))
(defn ck->components            [ck] (:components           (component-k->data-type ck)))
(defn ck->linked-property-types [ck] (:linked-property-type (component-k->data-type ck)))

(defn ck->doc [ck]
  (:doc (get component/attributes ck)))

(defn optional? [k]
  (let [optional? (get (get component/attributes k) :optional? :not-found)]
    (if (= optional? :not-found)
      true
      optional?)))

(defcomponent :boolean   {:widget :check-box  :schema :boolean :default-value true})
(defcomponent :some      {:widget :label      :schema :some})
(defcomponent :string    {:widget :text-field :schema :string})
(defcomponent :sound     {:widget :sound      :schema :string})
(defcomponent :image     {:widget :image      :schema :some})
(defcomponent :animation {:widget :animation  :schema :some})
(defcomponent :val-max   {:widget :text-field :schema (m/form val-max-schema)})
(defcomponent :number?   {:widget :text-field :schema number?})
(defcomponent :nat-int?  {:widget :text-field :schema nat-int?})
(defcomponent :int?      {:widget :text-field :schema int?})
(defcomponent :pos?      {:widget :text-field :schema pos?})
(defcomponent :pos-int?  {:widget :text-field :schema pos-int?})

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

(defn- m-map-schema [ks]
  (vec (concat [:map {:closed true}]
               (for [k ks]
                 [k {:optional (optional? k)} (ck->schema k)]))))

(defcomponent :map
  (component/data [[_ & ks]]
    {:widget :nested-map
     :schema (m-map-schema ks)}))

(defcomponent :components
  (component/data [[_ & ks]]
    {:widget :nested-map
     :schema (m-map-schema ks)
     :components ks}))

(defcomponent :components-ns
  (component/data [[_ k]]
    (let [ks (filter #(= (name k) (namespace %)) (keys component/attributes))]
      {:widget :nested-map
       :schema (m-map-schema ks)
       :components ks})))

(defn map-attribute-schema [id-attribute attr-ks]
  (let [schema-form (vec (concat [:map {:closed true} id-attribute]
                                 (for [k attr-ks]
                                   (vector k (ck->schema k)))))]
    (try (m/schema schema-form)
         (catch Throwable t
           (throw (ex-info "" {:schema-form schema-form} t))))))
