(ns core.data
  (:require [malli.core :as m]
            [utils.core :as utils :refer [safe-get]]
            [core.component :as component]
            [core.val-max :refer [val-max-schema]]))

; TODO be clear (just use namespaced keywords for all these things ...)
; m/schema
; or core/schema
; or data/type ?
; dont have 2 different :schema keys

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

(def defs {})

(defn defdata [k m]
  (when (get defs k)
    (println "WARNING: Overwriting defdata" k))
  (alter-var-root #'defs assoc k m)
  k)

(defn- ck->data-schema [ck]
  (try
   (let [core-schema (:schema (safe-get component/attributes ck))
         data-type (safe-get core.data/defs (if (vector? core-schema)
                                              (first core-schema)
                                              core-schema))]
     (if (map? data-type)
       data-type
       (data-type core-schema)))
   (catch Throwable t
     (throw (ex-info "" {:ck ck} t)))))

(defn ck->widget [ck]
  (:widget (ck->data-schema ck)))

(defn ck->schema [ck]
  (:schema (ck->data-schema ck)))

(defn ck->enum-items [ck]
  (:items (ck->data-schema ck)))

(defn ck->components [ck]
  (:components (ck->data-schema ck)))

(defn ck->linked-property-types [ck]
  (:linked-property-type (ck->data-schema ck)))

(defn ck->doc [ck]
  (:doc (get component/attributes ck)))

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
(defdata :enum (fn [[k & items :as schema]]
                 {:widget :enum
                  :schema schema
                  :items items}))

; TODO not checking if one of existing ids used
; widget requires property/image.
(defdata :one-to-many-ids
  (fn [[_ property-type]]
    {:widget :one-to-many
     :schema [:set :qualified-keyword] ; TODO namespace missing
     :linked-property-type property-type})) ; => fetch from schema namespaced ?

(defdata :qualified-keyword
  (fn [schema]
    {:widget :label
     :schema schema}))

(defn optional? [k]
  (let [optional? (get (get component/attributes k) :optional? :not-found)]
    (if (= optional? :not-found)
      true
      optional?)))

(defn- m-map-schema [ks]
  (vec (concat [:map {:closed true}]
               (for [k ks]
                 [k {:optional (optional? k)} (ck->schema k)]))))

(comment
 (ck->schema :creature/entity)
 (binding [*print-level* nil]
   (clojure.repl/pst *e)))

(defdata :map
  (fn [[_ & ks]]
    {:widget :nested-map
     :schema (m-map-schema ks)}))

(defdata :components
  (fn [[_ & ks]]
    {:widget :nested-map
     :schema (m-map-schema ks)
     :components ks}))

(defdata :components-ns
  (fn [[_ k]]
    (let [ks (filter #(= (name k) (namespace %)) (keys component/attributes))]
      {:widget :nested-map
       :schema (m-map-schema ks)
       :components ks})))

; TODO similar to map-attribute & components-attribute
(defn map-attribute-schema [id-attribute attr-ks]
  (let [schema-form (vec (concat [:map {:closed true} id-attribute] ; TODO same id-attribute w. different namespaces ...
                                 ; creature/id ?
                                 ; item/id ?
                                 (for [k attr-ks]
                                   (vector k (ck->schema k)))))]
    (try (m/schema schema-form)
         (catch Throwable t
           (throw (ex-info "" {:schema-form schema-form} t))))))


; (m/form entity/movement-speed-schema)


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






