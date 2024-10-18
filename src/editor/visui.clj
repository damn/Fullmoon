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

; TODO overview table not refreshed after changes in properties
; -> just rebuild everything after save?!

;;

(defn- k->default-value [k]
  (let [schema (schema/of k)]
    (cond
     (#{:s/one-to-one :s/one-to-many} (schema/type schema)) nil

     ;(#{:s/map} type) {} ; cannot have empty for required keys, then no Add Component button

     :else (mg/generate (schema/form schema) {:size 3}))))

;;

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

;;

;;

;; =>
; https://libgdx.com/wiki/graphics/2d/scene2d/table#inserting-cells
; => just rebuild the table/window !?

(declare attribute-label)

(defn- kv-widget [[k v] m-schema & {:keys [horizontal-sep?]}]
  (let [value-widget (widget/create (schema/of k) v)
        table (ui/table {:id k :cell-defaults {:pad 4}})
        column (remove nil?
                       [(when (malli/optional? k m-schema)
                          (ui/text-button "-" #(let [window (ui/find-ancestor-window table)]
                                                 (a/remove! table)
                                                 (.pack window))))
                        (attribute-label k)
                        (ui/vertical-separator-cell)
                        value-widget])
        rows [(when horizontal-sep? [(ui/horizontal-separator-cell (count column))])
              column]]
    (a/set-id! value-widget v)
    (ui/add-rows! table (remove nil? rows))
    table))

(defn- map-widget->data [group]
  (into {} (for [k (map a/id (ui/children group))
                 :let [table (k group)]]
             [k (widget/value (schema/of k)
                              (-> table ui/children last))])))

(defn- choose-component-window [m-schema map-widget]
  (fn []
    (let [window (ui/window {:title "Choose"
                             :modal? true
                             :close-button? true
                             :center? true
                             :close-on-escape? true
                             :cell-defaults {:pad 5}})
          remaining-ks (sort (remove (set (keys (map-widget->data map-widget)))
                                     (malli/map-keys m-schema)))]
      (ui/add-rows! window (for [k remaining-ks]
                             [(ui/text-button (name k)
                                              (fn []
                                                (a/remove! window)
                                                (ui/add-actor! map-widget
                                                               (kv-widget [k (k->default-value k)]
                                                                          m-schema
                                                                          :horizontal-sep?
                                                                          (pos? (count (ui/children map-widget)))))
                                                (ui/pack-ancestor-window! map-widget)))]))
      (.pack window)
      (stage-add! window))))

(defn- kv-widgets [m-schema props]
  (let [first-row? (atom true)]
    (for [[k v] (sort-by component-order props)
          :let [sep? (not @first-row?)
                _ (reset! first-row? false)]]
      (kv-widget [k v] m-schema :horizontal-sep? sep?))))

(defn- map-widget [m-schema props]
  (ui/vertical-group (kv-widgets m-schema props)))

(defn- value-widget [[k v]]
  (let [widget (widget/create (schema/of k) v)]
    (.setUserObject widget [k v])
    widget))

(def ^:private value-widget? (comp vector? a/id))

(defn- attribute-label [k m-schema]
  (let [label (ui/label (name k))
        delete-button (when (malli/optional? k m-schema)
                        (ui/text-button "[SCARLET]delete[] " (fn [])))]
    (when-let [doc (:editor/doc (component/meta k))]
      (ui/add-tooltip! label doc))
    (ui/table {:cell-defaults {:pad 2}
               :rows [[delete-button label]]})))

(defn- component-row [[k v] m-schema]
  [{:actor (attribute-label k m-schema)
    :right? true}
   (ui/vertical-separator-cell)
   {:actor (value-widget [k v])
    :left? true}])

(defn- horiz-sep []
  [(ui/horizontal-separator-cell 3)])

(comment
 (when (malli/optional? k m-schema)
   (ui/text-button "-" #(let [window (ui/find-ancestor-window table)]
                          (a/remove! table)
                          (.pack window)))))

(defn- interpose-f [f coll]
  (drop 1 (interleave (repeatedly f) coll)))

(defmethod widget/create :s/map [schema m]
  (ui/table {:cell-defaults {:pad 5}
             :id :map-widget
             :rows (interpose-f horiz-sep
                                (map #(component-row % (schema/form schema))
                                     (sort-by component-order m)))})

  #_(let [m-schema (schema/form schema)
        map-widget (map-widget m-schema m)
        optional-keys-left? (malli/optional-keys-left m-schema m)]
    (a/set-id! map-widget :map-widget)
    (ui/table {:cell-defaults {:pad 5}
               :rows (remove nil?
                             [(when optional-keys-left?
                                [(ui/text-button "Add component" (choose-component-window m-schema map-widget))])
                              (when optional-keys-left?
                                [(ui/horizontal-separator-cell 1)])
                              [map-widget]])})))

; * get values out
; * add component-row (rebuild -> will be at right place)
; * remove component-row

(comment
 (let [window (:property-editor-window (gdx.ui.stage-screen/stage-get))
       table (.findActor (:scroll-pane window) "scroll-pane-table")
       map-widget (:map-widget table)]
   (widget/value :s/map map-widget))
 )

(defmethod widget/value :s/map [_ table]
  (into {}
        (for [widget (filter value-widget? (ui/children table))
              :let [[k _] (a/id widget)]]
          [k (widget/value (schema/of k) widget)])))

(defn- apply-context-fn [window f]
  #(try (f)
        (a/remove! window)
        (catch Throwable t
          (error-window! t))))

(defn property-editor-window [id]
  (let [props (safe-get db/db id)
        schema (schema/of (property/type props))
        window (ui/window {:title "Edit Property"
                           :id :property-editor-window
                           :modal? true
                           :close-button? true
                           :center? true
                           :close-on-escape? true
                           :cell-defaults {:pad 5}})
        widget (widget/create schema props)
        save!   (apply-context-fn window #(db/update! (widget/value schema widget)))
        delete! (apply-context-fn window #(db/delete! id))]
    (ui/add-rows! window [[(scroll-pane-cell [[{:actor (ui/text-button "[LIME]Save[] [LIGHT_GRAY](ENTER)[]" save!)
                                                :left? true}
                                               {:actor (ui/text-button "[SCARLET]Delete[]" delete!)
                                                :right? true}]
                                              [{:actor widget :colspan 2}]])]])
    (ui/add-actor! window (ui/actor {:act (fn []
                                            (when (key-just-pressed? :enter)
                                              (save!)))}))
    (.pack window)
    window))
