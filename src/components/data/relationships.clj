(ns components.data.relationships
  (:require [core.component :as component :refer [defcomponent]]
            [core.components :as components]
            [core.context :as ctx]
            [core.data :as data]
            [core.property :as property]
            [core.scene2d.actor :as actor]
            [core.scene2d.group :as group]
            [core.scene2d.ui.table :as table]
            [core.scene2d.ui.widget-group :refer [pack!]]))

; TODO schemas not checking if exists

(defcomponent :one-to-many
  (component/->data [[_ property-type]]
    {:schema [:set [:qualified-keyword {:namespace (property/property-type->id-namespace property-type)}]]
     :linked-property-type property-type}))

(defmethod data/edn->value :one-to-many [_ property-ids ctx]
  (map (partial ctx/property ctx) property-ids))

(defcomponent :one-to-one
  (component/->data [[_ property-type]]
    {:schema [:qualified-keyword {:namespace (property/property-type->id-namespace property-type)}]
     :linked-property-type property-type}))

(defmethod data/edn->value :one-to-one [_ property-id ctx]
  (ctx/property ctx property-id))

(defn- add-one-to-many-rows [ctx table property-type property-ids]
  (let [redo-rows (fn [ctx property-ids]
                    (group/clear-children! table)
                    (add-one-to-many-rows ctx table property-type property-ids)
                    (actor/pack-ancestor-window! table))]
    (table/add-rows!
     table
     [[(ctx/->text-button ctx "+"
                          (fn [ctx]
                            (let [window (ctx/->window ctx {:title "Choose"
                                                            :modal? true
                                                            :close-button? true
                                                            :center? true
                                                            :close-on-escape? true})
                                  clicked-id-fn (fn [ctx id]
                                                  (actor/remove! window)
                                                  (redo-rows ctx (conj property-ids id))
                                                  ctx)]
                              (table/add! window (ctx/->overview-table ctx property-type clicked-id-fn))
                              (pack! window)
                              (ctx/add-to-stage! ctx window))))]
      (for [property-id property-ids]
        (let [property (ctx/property ctx property-id)
              image-widget (ctx/->image-widget ctx
                                               (property/->image property)
                                               {:id property-id})]
          (actor/add-tooltip! image-widget #(components/info-text property %))
          image-widget))
      (for [id property-ids]
        (ctx/->text-button ctx "-" #(do (redo-rows % (disj property-ids id)) %)))])))

(defmethod data/->widget :one-to-many [[_ data] property-ids context]
  (let [table (ctx/->table context {:cell-defaults {:pad 5}})]
    (add-one-to-many-rows context
                          table
                          (:linked-property-type data)
                          property-ids)
    table))

(defmethod data/widget->value :one-to-many [_ widget]
  (->> (group/children widget)
       (keep actor/id)
       set))

(defn- add-one-to-one-rows [ctx table property-type property-id]
  (let [redo-rows (fn [ctx id]
                    (group/clear-children! table)
                    (add-one-to-one-rows ctx table property-type id)
                    (actor/pack-ancestor-window! table))]
    (table/add-rows!
     table
     [[(when-not property-id
         (ctx/->text-button ctx "+"
                            (fn [ctx]
                              (let [window (ctx/->window ctx {:title "Choose"
                                                              :modal? true
                                                              :close-button? true
                                                              :center? true
                                                              :close-on-escape? true})
                                    clicked-id-fn (fn [ctx id]
                                                    (actor/remove! window)
                                                    (redo-rows ctx id)
                                                    ctx)]
                                (table/add! window (ctx/->overview-table ctx property-type clicked-id-fn))
                                (pack! window)
                                (ctx/add-to-stage! ctx window)))))]
      [(when property-id
         (let [property (ctx/property ctx property-id)
               image-widget (ctx/->image-widget ctx
                                                (property/->image property)
                                                {:id property-id})]
           (actor/add-tooltip! image-widget #(components/info-text property %))
           image-widget))]
      [(when property-id
         (ctx/->text-button ctx "-" #(do (redo-rows % nil) %)))]])))

(defmethod data/->widget :one-to-one [[_ data] property-id ctx]
  (let [table (ctx/->table ctx {:cell-defaults {:pad 5}})]
    (add-one-to-one-rows ctx
                         table
                         (:linked-property-type data)
                         property-id)
    table))

(defmethod data/widget->value :one-to-one [_ widget]
  (->> (group/children widget) (keep actor/id) first))
