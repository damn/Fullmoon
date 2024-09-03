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

; TODO schema not checking if exists
(defcomponent :one-to-many
  (component/->data [[_ property-type]]
    {:schema [:set [:qualified-keyword]]
     :linked-property-type property-type
     :fetch-references (fn [ctx property-ids]
                         (map (partial ctx/property ctx)
                              property-ids))}))

; TODO schema not checking if exists
(defcomponent :one-to-one
  (component/->data [[_ property-type]]
    {:schema [:qualified-keyword]
     :linked-property-type property-type
     :fetch-references ctx/property}))

(defn- add-one-to-many-rows [ctx table property-type properties]
  (let [redo-rows (fn [ctx property-ids]
                    (group/clear-children! table)
                    (add-one-to-many-rows ctx table property-type (map #(ctx/property ctx %) property-ids))
                    (actor/pack-ancestor-window! table))
        property-ids (set (map :property/id properties))]
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
      (for [property properties]
        (let [image-widget (ctx/->image-widget ctx ; image-button/link?
                                               (property/->image property)
                                               {:id (:property/id property)})]
          (actor/add-tooltip! image-widget #(components/info-text property %))
          image-widget))
      (for [{:keys [property/id]} properties]
        (ctx/->text-button ctx "-" #(do (redo-rows % (disj property-ids id)) %)))])))

(defmethod data/->widget :one-to-many [[_ data] properties context]
  (let [table (ctx/->table context {:cell-defaults {:pad 5}})]
    (add-one-to-many-rows context
                          table
                          (:linked-property-type data)
                          properties)
    table))

; TODO use id of the value-widget itself and set/change it
(defmethod data/widget->value :one-to-many [_ widget]
  (->> (group/children widget)
       (keep actor/id)
       set))

(defn- add-one-to-one-rows [ctx table property-type property]
  (let [redo-rows (fn [ctx id]
                    (group/clear-children! table)
                    (add-one-to-one-rows ctx table property-type (when id (ctx/property ctx id)))
                    (actor/pack-ancestor-window! table))]
    (table/add-rows!
     table
     [[(when-not property
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
      [(when property
         (let [image-widget (ctx/->image-widget ctx ; image-button/link?
                                                (property/->image property)
                                                {:id (:property/id property)})]
           (actor/add-tooltip! image-widget #(components/info-text property %))
           image-widget))]
      [(when property
         (ctx/->text-button ctx "-" #(do (redo-rows % nil) %)))]])))

; TODO DRY with one-to-many
(defmethod data/->widget :one-to-one [[_ data] property ctx]
  (let [table (ctx/->table ctx {:cell-defaults {:pad 5}})]
    (add-one-to-one-rows ctx
                         table
                         (:linked-property-type data)
                         property)
    table))

(defmethod data/widget->value :one-to-one [_ widget]
  (->> (group/children widget) (keep actor/id) first))
