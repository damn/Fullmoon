(ns core.data
  (:require [malli.core :as m]
            [utils.core :as utils]
            [core.component :as component]
            [core.val-max :refer [val-max-schema]]))

; data itself because a component (miltimethod widget,schema, ...)
; it snot schema but :data :data/foo

; :grep component/attributes
; because is not correct anymore its not a malli schema .....
; but a core schema ???


; we need
; :widget
; :schema (malli)
; items/components/default-value

; TODO its not schema but :data
; see :val-max / sound / image

(defdata :boolean   {:widget :check-box  :schema :boolean :default-value true})

(defdata :some      {:widget :label      :schema :some})

(defdata :string    {:widget :text-field :schema :string})

(defdata :sound     {:widget :sound      :schema :string})

(defdata :image     {:widget :image      :schema :some})

(defdata :animation {:widget :animation  :schema :some})

(defdata :val-max   {:widget :text-field :schema (m/form val-max-schema)})

(defdata :number?  {:widget :text-field :schema number?})
(defdata :nat-int? {:widget :text-field :schema nat-int?})
(defdata :int?     {:widget :text-field :schema int?})
(defdata :pos?     {:widget :text-field :schema pos?})
(defdata :pos-int? {:widget :text-field :schema pos-int?})

; derive from mnumber
; and use same malli schema
; and just identity ??/
; but case statements so i know hwats going on


; [:enum :good :evil]
; :schema function and just use identity in many cases??
(defdata :enum  {:widget :enum
                 :schema identity
                 :items rest})


; TODO not checking if one of existing ids used
; widget requires property/image.
(defdata :one-to-many-ids
  {:widget :one-to-many
   :schema [:set :qualified-keyword] ; TODO namespace missing
   :linked-property-type second}) ; => fetch from schema namespaced ?

(defn optional? [k]
  (let [optional? (get (get component/attributes k) :optional? :not-found)]
    (if (= optional? :not-found)
      true
      optional?)))

(defn- m-map-schema [ks]
  (vec (concat [:map {:closed true}]
               (for [k ks]
                 [k {:optional (optional? k)} (ck->schema k)]))))

; TODO similar to components-attribute
(defdata :map
  {:widget :nested-map
   :schema #(m-map-schema (rest %))})



; TODO be clear
; m/schema
; or core/schema
; or data/type ?
; dont have 2 different :schema keys

(defdata :components
  {:widget :nested-map
   :schema #(m-map-schema (rest %))
   :components rest})

(defn- kw-ns->components [[_ k]]
  (filter #(= (name k) (namespace %)) (keys component/attributes)))

(defdata :components-ns
  {:widget :nested-map
   :schema #(m-map-schema (kw-ns->components %))
   :components kw-ns->components})

; TODO similar to map-attribute & components-attribute
(defn map-attribute-schema [id-attribute attr-ks]
  (m/schema
   (vec (concat [:map {:closed true} id-attribute] ; TODO same id-attribute w. different namespaces ...
                ; creature/id ?
                ; item/id ?
                (for [k attr-ks]
                  (vector k (ck->schema k)))))))

; [:components-ns :effect] ; gives ns
; [:components operations] ; gives keys

; [:one-to-many-ids :properties/item]
; [:one-to-many-ids :properties/skill]

; (m/form entity/movement-speed-schema)

; [:map :damage/min-max]

; [:qualified-keyword {:namespace :species}]

; [:maybe :pos-int?]


; TODO :derive add to defcomponent map ? (used at val-max)

; Next, see :context/properties
; can move defcomponents into app.edn itself

; also defstats -> effects & operations should be deducted from :schema
; and stats itself can be passed @ created in resources/app.edn

; * which data is there? (for properties - or in general)
; * do they all have proper widget/schema/optional?
; * define not at entity/foo but at components/properties ? (creature state different than entity/state ..)

; => just pass :schema/:optional?/:doc
; from schema calculate :widget (my use cases first)
; remove core.data dependencies

; TODO validate value-type-schema
; => do @ specific component types, write extra macro
; context/def or entity/def etc.
;(assert (:schema attr-map) k) (not all this, ...)
;(assert (:widget attr-map) k)
; optional: :doc ! (maybe not optional make ...)






