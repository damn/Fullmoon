(ns ^:no-doc editor.visui
  (:require [clojure.edn :as edn]
            [clojure.set :as set]
            [component.core :as component]
            [component.db :as db]
            [component.info :as info]
            [component.property :as property]
            [component.schema :as schema]
            [editor.overview :refer [overview-table]]
            [editor.utils :refer [scroll-pane-cell]]
            [editor.widget :as widget]
            editor.widgets
            [gdx.input :refer [key-just-pressed?]]
            [gdx.ui :as ui]
            [gdx.ui.actor :as a]
            [gdx.ui.error-window :refer [error-window!]]
            [gdx.ui.stage-screen :as stage-screen :refer [stage-add!]]
            [gdx.screen :as screen]
            [malli.core :as m]
            [malli.generator :as mg]
            [utils.core :refer [safe-get index-of]]))

; TODO items dont refresh on clicking tab -!
; TODO main properties optional keys to add them itself not possible (e.g. to add skill/cooldown back)
; TODO save button show if changes made, otherwise disabled?
; when closing (lose changes? yes no)
; TODO overview table not refreshed after changes in property editor window
; * don't show button if no components to add anymore (use remaining-ks)
; * what is missing to remove the button once the last optional key was added (not so important)
; maybe check java property/game/db/editors .... unity? rpgmaker? gamemaker?

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

(declare ->component-widget
         attribute-widget-group->data)

(defn- k-properties [schema]
  (let [[_m _p & ks] (m/form schema)]
    (into {} (for [[k m? _schema] ks]
               [k (if (map? m?) m?)]))))

(defn- map-keys [schema]
  (let [[_m _p & ks] (m/form schema)]
    (for [[k m? _schema] ks]
      k)))

(defn- k->default-value [k]
  (let [schema (schema/of k)]
    (cond
     (#{:s/one-to-one :s/one-to-many} schema) nil
     ;(#{:s/map} type) {} ; cannot have empty for required keys, then no Add Component button
     :else (mg/generate (schema/form schema) {:size 3}))))

(defn- ->choose-component-window [schema attribute-widget-group]
  (fn []
    (let [k-props (k-properties schema)
          window (ui/window {:title "Choose"
                             :modal? true
                             :close-button? true
                             :center? true
                             :close-on-escape? true
                             :cell-defaults {:pad 5}})
          remaining-ks (sort (remove (set (keys (attribute-widget-group->data attribute-widget-group)))
                                     (map-keys schema)))]
      (ui/add-rows! window (for [k remaining-ks]
                             [(ui/text-button (name k)
                                              (fn []
                                                (a/remove! window)
                                                (ui/add-actor! attribute-widget-group
                                                               (->component-widget [k (get k-props k) (k->default-value k)]
                                                                                   :horizontal-sep?
                                                                                   (pos? (count (ui/children attribute-widget-group)))))
                                                (ui/pack-ancestor-window! attribute-widget-group)))]))
      (.pack window)
      (stage-add! window))))

(declare ->attribute-widget-group)

(defn- optional-keyset [schema]
  (set (map first
            (filter (fn [[k prop-m]] (:optional prop-m))
                    (k-properties schema)))))

(defmethod widget/create :s/map [schema m]
  (let [schema (schema/form schema)
        attribute-widget-group (->attribute-widget-group schema m)
        optional-keys-left? (seq (set/difference (optional-keyset schema)
                                                 (set (keys m))))]
    (a/set-id! attribute-widget-group :attribute-widget-group)
    (ui/table {:cell-defaults {:pad 5}
               :rows (remove nil?
                             [(when optional-keys-left?
                                [(ui/text-button "Add component" (->choose-component-window schema attribute-widget-group))])
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

(defn- ->component-widget [[k k-props v] & {:keys [horizontal-sep?]}]
  (let [label (->attribute-label k)
        value-widget (widget/create (schema/of k) v)
        table (ui/table {:id k :cell-defaults {:pad 4}})
        column (remove nil?
                       [(when (:optional k-props)
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

(defn- ->component-widgets [schema props]
  (let [first-row? (atom true)
        k-props (k-properties schema)]
    (for [[k v] (sort-by component-order props)
          :let [sep? (not @first-row?)
                _ (reset! first-row? false)]]
      (->component-widget [k (get k-props k) v] :horizontal-sep? sep?))))

(defn- ->attribute-widget-group [schema props]
  (ui/vertical-group (->component-widgets schema props)))

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

(defn- ->property-editor-window [id]
  (let [props (safe-get db/db id)
        window (ui/window {:title "Edit Property"
                           :modal? true
                           :close-button? true
                           :center? true
                           :close-on-escape? true
                           :cell-defaults {:pad 5}})
        widgets (->attribute-widget-group (property/schema props) props)
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

(import '(com.kotcrab.vis.ui.widget.tabbedpane Tab TabbedPane TabbedPaneAdapter))

(defn- ->tab [{:keys [title content savable? closable-by-user?]}]
  (proxy [Tab] [(boolean savable?) (boolean closable-by-user?)]
    (getTabTitle [] title)
    (getContentTable [] content)))

(defn- ->tabbed-pane [tabs-data]
  (let [main-table (ui/table {:fill-parent? true})
        container (ui/table {})
        tabbed-pane (TabbedPane.)]
    (.addListener tabbed-pane
                  (proxy [TabbedPaneAdapter] []
                    (switchedTab [^Tab tab]
                      (.clearChildren container)
                      (.fill (.expand (.add container (.getContentTable tab)))))))
    (.fillX (.expandX (.add main-table (.getTable tabbed-pane))))
    (.row main-table)
    (.fill (.expand (.add main-table container)))
    (.row main-table)
    (.pad (.left (.add main-table (ui/label "[LIGHT_GRAY]Left-Shift: Back to Main Menu[]"))) (float 10))
    (doseq [tab-data tabs-data]
      (.add tabbed-pane (->tab tab-data)))
    main-table))

(defn- ->tabs-data []
  (for [property-type (sort (property/types))]
    {:title (:title (property/overview property-type))
     :content (overview-table property-type (fn [property-id]
                                              (stage-add! (->property-editor-window property-id))))}))

(defn screen [->background-image]
  [:screens/property-editor
   (stage-screen/create :actors
                        [(->background-image)
                         (->tabbed-pane (->tabs-data))
                         (ui/actor {:act (fn []
                                           (when (key-just-pressed? :shift-left)
                                             (screen/change! :screens/main-menu)))})])])
