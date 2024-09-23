(ns core.property.data.relationships
  (:require [core.component :as component :refer [defcomponent]]
            [core.ctx.property :as property]
            [core.screens.stage :as stage]
            [core.screens.property-editor :refer [->overview-table]]
            [core.ui.actor :as actor]
            [core.ui.group :as group]
            [core.ui :as ui]))

; TODO schemas not checking if that property exists in db...
; https://github.com/damn/core/issues/59

(defcomponent :one-to-many
  (property/->value [[_ property-type]]
    {:schema [:set [:qualified-keyword {:namespace (property/property-type->id-namespace property-type)}]]}))

(defn one-to-many-schema->linked-property-type [[_set [_qualif_kw {:keys [namespace]}]]]
  (property/ns-k->property-type namespace))

(comment
 (= (one-to-many-schema->linked-property-type [:set [:qualified-keyword {:namespace :items}]])
    :properties/items)
 )

(defmethod property/edn->value :one-to-many [_ property-ids ctx]
  (map (partial property/build ctx) property-ids))

(defcomponent :one-to-one
  (property/->value [[_ property-type]]
    {:schema [:qualified-keyword {:namespace (property/property-type->id-namespace property-type)}]}))

(defn one-to-one-schema->linked-property-type [[_qualif_kw {:keys [namespace]}]]
  (property/ns-k->property-type namespace))

(comment
 (= (one-to-one-schema->linked-property-type [:qualified-keyword {:namespace :creatures}])
    :properties/creatuers)
 )

(defmethod property/edn->value :one-to-one [_ property-id ctx]
  (property/build ctx property-id))

(defn- add-one-to-many-rows [ctx table property-type property-ids]
  (let [redo-rows (fn [ctx property-ids]
                    (group/clear-children! table)
                    (add-one-to-many-rows ctx table property-type property-ids)
                    (actor/pack-ancestor-window! table))]
    (ui/add-rows!
     table
     [[(ui/->text-button ctx "+"
                         (fn [ctx]
                           (let [window (ui/->window {:title "Choose"
                                                      :modal? true
                                                      :close-button? true
                                                      :center? true
                                                      :close-on-escape? true})
                                 clicked-id-fn (fn [ctx id]
                                                 (actor/remove! window)
                                                 (redo-rows ctx (conj property-ids id))
                                                 ctx)]
                             (ui/add! window (->overview-table ctx property-type clicked-id-fn))
                             (.pack window)
                             (stage/add-actor! ctx window))))]
      (for [property-id property-ids]
        (let [property (property/build ctx property-id)
              image-widget (ui/->image-widget (property/->image property)
                                              {:id property-id})]
          (actor/add-tooltip! image-widget #(component/->text property %))
          image-widget))
      (for [id property-ids]
        (ui/->text-button ctx "-" #(do (redo-rows % (disj property-ids id)) %)))])))

(defmethod property/->widget :one-to-many [[_ data] property-ids context]
  (let [table (ui/->table {:cell-defaults {:pad 5}})]
    (add-one-to-many-rows context
                          table
                          (one-to-many-schema->linked-property-type (:schema data))
                          property-ids)
    table))

(defmethod property/widget->value :one-to-many [_ widget]
  (->> (group/children widget)
       (keep actor/id)
       set))

(defn- add-one-to-one-rows [ctx table property-type property-id]
  (let [redo-rows (fn [ctx id]
                    (group/clear-children! table)
                    (add-one-to-one-rows ctx table property-type id)
                    (actor/pack-ancestor-window! table))]
    (ui/add-rows!
     table
     [[(when-not property-id
         (ui/->text-button ctx "+"
                           (fn [ctx]
                             (let [window (ui/->window {:title "Choose"
                                                        :modal? true
                                                        :close-button? true
                                                        :center? true
                                                        :close-on-escape? true})
                                   clicked-id-fn (fn [ctx id]
                                                   (actor/remove! window)
                                                   (redo-rows ctx id)
                                                   ctx)]
                               (ui/add! window (->overview-table ctx property-type clicked-id-fn))
                               (.pack window)
                               (stage/add-actor! ctx window)))))]
      [(when property-id
         (let [property (property/build ctx property-id)
               image-widget (ui/->image-widget (property/->image property)
                                               {:id property-id})]
           (actor/add-tooltip! image-widget #(component/->text property %))
           image-widget))]
      [(when property-id
         (ui/->text-button ctx "-" #(do (redo-rows % nil) %)))]])))

(defmethod property/->widget :one-to-one [[_ data] property-id ctx]
  (let [table (ui/->table {:cell-defaults {:pad 5}})]
    (add-one-to-one-rows ctx
                         table
                         (one-to-one-schema->linked-property-type (:schema data))
                         property-id)
    table))

(defmethod property/widget->value :one-to-one [_ widget]
  (->> (group/children widget) (keep actor/id) first))
