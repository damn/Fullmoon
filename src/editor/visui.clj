(ns ^:no-doc editor.visui
  (:require [component.core :as component]
            [component.db :as db]
            [component.info :as info]
            [component.property :as property]
            [component.schema :as schema]
            [editor.malli :as malli]
            [editor.utils :refer [scroll-pane-cell]]
            [editor.widget :as widget]
            editor.widgets
            [gdx.input :refer [key-just-pressed?]]
            [gdx.ui :as ui]
            [gdx.ui.actor :as a]
            [gdx.ui.error-window :refer [error-window!]]
            [gdx.ui.stage-screen :refer [stage-add!]]
            [malli.core :as m]
            [malli.generator :as mg]
            [utils.core :refer [safe-get index-of]]))

; TODO optional attributes in a property cannot be added again after remove -> reuse :s/map
; TODO overview table not refreshed after changes in properties

(declare ->component-widget
         attribute-widget-group->data)

(defn- k->default-value [k]
  (let [schema (schema/of k)]
    (cond
     (#{:s/one-to-one :s/one-to-many} schema) nil
     ;(#{:s/map} type) {} ; cannot have empty for required keys, then no Add Component button
     :else (mg/generate (schema/form schema) {:size 3}))))

(defn- ->choose-component-window [m-schema attribute-widget-group]
  (fn []
    (let [window (ui/window {:title "Choose"
                             :modal? true
                             :close-button? true
                             :center? true
                             :close-on-escape? true
                             :cell-defaults {:pad 5}})
          remaining-ks (sort (remove (set (keys (attribute-widget-group->data attribute-widget-group)))
                                     (malli/map-keys m-schema)))]
      (ui/add-rows! window (for [k remaining-ks]
                             [(ui/text-button (name k)
                                              (fn []
                                                (a/remove! window)
                                                (ui/add-actor! attribute-widget-group
                                                               (->component-widget [k (k->default-value k)]
                                                                                   m-schema
                                                                                   :horizontal-sep?
                                                                                   (pos? (count (ui/children attribute-widget-group)))))
                                                (ui/pack-ancestor-window! attribute-widget-group)))]))
      (.pack window)
      (stage-add! window))))

(declare ->attribute-widget-group)

(defmethod widget/create :s/map [schema m]
  (let [m-schema (schema/form schema)
        attribute-widget-group (->attribute-widget-group m-schema m)
        optional-keys-left? (malli/optional-keys-left m-schema m)]
    (a/set-id! attribute-widget-group :attribute-widget-group)
    (ui/table {:cell-defaults {:pad 5}
               :rows (remove nil?
                             [(when optional-keys-left?
                                [(ui/text-button "Add component" (->choose-component-window m-schema attribute-widget-group))])
                              (when optional-keys-left?
                                [(ui/horizontal-separator-cell 1)])
                              [attribute-widget-group]])})))


(defmethod widget/value :s/map [_ table]
  (attribute-widget-group->data (:attribute-widget-group table)))

(defn- ->attribute-label [k]
  (let [label (ui/label (str k))]
    (when-let [doc (:editor/doc (component/meta k))]
      (ui/add-tooltip! label doc))
    label))

(defn- ->component-widget [[k v] m-schema & {:keys [horizontal-sep?]}]
  (let [label (->attribute-label k)
        value-widget (widget/create (schema/of k) v)
        table (ui/table {:id k :cell-defaults {:pad 4}})
        column (remove nil?
                       [(when (malli/optional? k m-schema)
                          (ui/text-button "-" #(let [window (ui/find-ancestor-window table)]
                                                 (a/remove! table)
                                                 (.pack window))))
                        label
                        (ui/vertical-separator-cell)
                        value-widget])
        rows [(when horizontal-sep? [(ui/horizontal-separator-cell (count column))])
              column]]
    (a/set-id! value-widget v)
    (ui/add-rows! table (remove nil? rows))
    table))

(defn- attribute-widget-table->value-widget [table]
  (-> table ui/children last))

(def ^:private property-k-sort-order
  [:property/id
   :property/pretty-name
   :app/lwjgl3
   :entity/image
   :entity/animation
   :creature/species
   :creature/level
   :entity/body
   :item/slot
   :projectile/speed
   :projectile/max-range
   :projectile/piercing?
   :skill/action-time-modifier-key
   :skill/action-time
   :skill/start-action-sound
   :skill/cost
   :skill/cooldown])

(defn- component-order [[k _v]]
  (or (index-of k property-k-sort-order) 99))

(defn- ->component-widgets [m-schema props]
  (let [first-row? (atom true)]
    (for [[k v] (sort-by component-order props)
          :let [sep? (not @first-row?)
                _ (reset! first-row? false)]]
      (->component-widget [k v] m-schema :horizontal-sep? sep?))))

(defn- ->attribute-widget-group [m-schema props]
  (ui/vertical-group (->component-widgets m-schema props)))

(defn- attribute-widget-group->data [group]
  (into {} (for [k (map a/id (ui/children group))
                 :let [table (k group)
                       value-widget (attribute-widget-table->value-widget table)]]
             [k (widget/value (schema/of k) value-widget)])))

(defn- apply-context-fn [window f]
  #(try (f)
        (a/remove! window)
        (catch Throwable t
          (error-window! t))))

(defn property-editor-window [id]
  (let [props (safe-get db/db id)
        window (ui/window {:title "Edit Property"
                           :modal? true
                           :close-button? true
                           :center? true
                           :close-on-escape? true
                           :cell-defaults {:pad 5}})
        widgets (->attribute-widget-group (property/m-schema props) props)
        save!   (apply-context-fn window #(db/update! (attribute-widget-group->data widgets)))
        delete! (apply-context-fn window #(db/delete! id))]
    (ui/add-rows! window [[(scroll-pane-cell [[{:actor widgets :colspan 2}]
                                              [(ui/text-button "Save [LIGHT_GRAY](ENTER)[]" save!)
                                               (ui/text-button "Delete" delete!)]])]])
    (ui/add-actor! window (ui/actor {:act (fn []
                                            (when (key-just-pressed? :enter)
                                              (save!)))}))
    (.pack window)
    window))
