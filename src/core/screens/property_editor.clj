(ns ^:no-doc core.screens.property-editor
  (:require [clojure.set :as set]
            [malli.core :as m]
            [malli.generator :as mg]
            [core.utils.core :as utils]
            [core.ctx :refer :all]
            [core.property :as property]
            [core.screens :as screens]
            [core.stage :as stage]
            [core.widgets.error-modal :refer [error-window!]]
            [core.actor :as actor]
            [core.group :as group]
            [core.ui :as ui])
  (:import com.badlogic.gdx.Input$Keys))

; TODO main properties optional keys to add them itself not possible (e.g. to add skill/cooldown back)
; TODO save button show if changes made, otherwise disabled?
; when closing (lose changes? yes no)
; TODO overview table not refreshed after changes in property editor window
; * don't show button if no components to add anymore (use remaining-ks)
; * what is missing to remove the button once the last optional key was added (not so important)
; maybe check java property/game/db/editors .... unity? rpgmaker? gamemaker?

; put together with core.components info-text-order ?
(def ^:private k-sort-order [:property/id
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
  (or (index-of k k-sort-order) 99))

(defn- truncate [s limit]
  (if (> (count s) limit)
    (str (subs s 0 limit) "...")
    s))

(defmethod property/->widget :default [_ v _ctx]
  (ui/->label (truncate (utils/->edn-str v) 60)))

(defmethod property/widget->value :default [_ widget]
  (actor/id widget))

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
  (let [[data-type {:keys [schema]}] (property/data-component k)]
    (cond
     (#{:one-to-one :one-to-many} data-type) nil
     ;(#{:map} data-type) {} ; cannot have empty for required keys, then no Add Component button
     :else (mg/generate schema {:size 3}))))

(defn- ->choose-component-window [data attribute-widget-group]
  (fn [ctx]
    (let [k-props (k-properties (:schema data))
          window (ui/->window {:title "Choose"
                               :modal? true
                               :close-button? true
                               :center? true
                               :close-on-escape? true
                               :cell-defaults {:pad 5}})
          remaining-ks (sort (remove (set (keys (attribute-widget-group->data attribute-widget-group)))
                                     (map-keys (:schema data))))]
      (ui/add-rows! window (for [k remaining-ks]
                             [(ui/->text-button (name k)
                                                (fn [ctx]
                                                  (actor/remove! window)
                                                  (group/add-actor! attribute-widget-group
                                                                    (->component-widget ctx
                                                                                        [k (get k-props k) (k->default-value k)]
                                                                                        :horizontal-sep?
                                                                                        (pos? (count (group/children attribute-widget-group)))))
                                                  (actor/pack-ancestor-window! attribute-widget-group)
                                                  ctx))]))
      (.pack window)
      (stage/add-actor! ctx window))))

(declare ->attribute-widget-group)

(defn- optional-keyset [schema]
  (set (map first
            (filter (fn [[k prop-m]] (:optional prop-m))
                    (k-properties schema)))))

(defmethod property/->widget :map [[_ data] m ctx]
  (let [attribute-widget-group (->attribute-widget-group ctx (:schema data) m)
        optional-keys-left? (seq (set/difference (optional-keyset (:schema data))
                                                 (set (keys m))))]
    (actor/set-id! attribute-widget-group :attribute-widget-group)
    (ui/->table {:cell-defaults {:pad 5}
                 :rows (remove nil?
                               [(when optional-keys-left?
                                  [(ui/->text-button "Add component"
                                                     (->choose-component-window data attribute-widget-group))])
                                (when optional-keys-left?
                                  [(ui/->horizontal-separator-cell 1)])
                                [attribute-widget-group]])})))


(defmethod property/widget->value :map [_ table]
  (attribute-widget-group->data (:attribute-widget-group table)))

(defn- ->attribute-label [k]
  (let [label (ui/->label (str k))]
    (when-let [doc (:editor/doc (get component-attributes k))]
      (actor/add-tooltip! label doc))
    label))

(defn- ->component-widget [ctx [k k-props v] & {:keys [horizontal-sep?]}]
  (let [label (->attribute-label k)
        value-widget (property/->widget (property/data-component k) v ctx)
        table (ui/->table {:id k
                           :cell-defaults {:pad 4}})
        column (remove nil?
                       [(when (:optional k-props)
                          (ui/->text-button "-" (fn [ctx]
                                                  (let [window (actor/find-ancestor-window table)]
                                                    (actor/remove! table)
                                                    (.pack window))
                                                  ctx)))
                        label
                        (ui/->vertical-separator-cell)
                        value-widget])
        rows [(when horizontal-sep? [(ui/->horizontal-separator-cell (count column))])
              column]]
    (actor/set-id! value-widget v)
    (ui/add-rows! table (remove nil? rows))
    table))

(defn- attribute-widget-table->value-widget [table]
  (-> table group/children last))

(defn- ->component-widgets [ctx schema props]
  (let [first-row? (atom true)
        k-props (k-properties schema)]
    (for [[k v] (sort-by component-order props)
          :let [sep? (not @first-row?)
                _ (reset! first-row? false)]]
      (->component-widget ctx [k (get k-props k) v] :horizontal-sep? sep?))))

(defn- ->attribute-widget-group [ctx schema props]
  (ui/->vertical-group (->component-widgets ctx schema props)))

(defn- attribute-widget-group->data [group]
  (into {} (for [k (map actor/id (group/children group))
                 :let [table (k group)
                       value-widget (attribute-widget-table->value-widget table)]]
             [k (property/widget->value (property/data-component k) value-widget)])))

;;

(defn- apply-context-fn [window f]
  (fn [ctx]
    (try
     (let [ctx (f ctx)]
       (actor/remove! window)
       ctx)
     (catch Throwable t
       (error-window! ctx t)))))

(defn- ->property-editor-window [ctx id]
  (let [props (utils/safe-get (:db (:context/properties ctx)) id)
        window (ui/->window {:title "Edit Property"
                             :modal? true
                             :close-button? true
                             :center? true
                             :close-on-escape? true
                             :cell-defaults {:pad 5}})
        widgets (->attribute-widget-group ctx (property/->schema props) props)
        save!   (apply-context-fn window #(property/update! % (attribute-widget-group->data widgets)))
        delete! (apply-context-fn window #(property/delete! % id))]
    (ui/add-rows! window [[(ui/->scroll-pane-cell ctx [[{:actor widgets :colspan 2}]
                                                       [(ui/->text-button "Save [LIGHT_GRAY](ENTER)[]" save!)
                                                        (ui/->text-button "Delete" delete!)]])]])
    (group/add-actor! window
                      (ui/->actor {:act (fn [_ctx]
                                          (when (.isKeyJustPressed gdx-input Input$Keys/ENTER)
                                            (swap! app-state save!)))}))
    (.pack window)
    window))

(defn- ->overview-property-widget [{:keys [property/id] :as props} clicked-id-fn extra-info-text scale]
  (let [on-clicked #(clicked-id-fn % id)
        button (if-let [image (property/->image props)]
                 (ui/->image-button image on-clicked {:scale scale})
                 (ui/->text-button (name id) on-clicked))
        top-widget (ui/->label (or (and extra-info-text (extra-info-text props)) ""))
        stack (ui/->stack [button top-widget])]
    (actor/add-tooltip! button #(->info-text props %))
    (actor/set-touchable! top-widget :disabled)
    stack))

(defn- ->overview-table [ctx property-type clicked-id-fn]
  (let [{:keys [sort-by-fn
                extra-info-text
                columns
                image/scale]} (property/overview property-type)
        properties (property/all-properties ctx property-type)
        properties (if sort-by-fn
                     (sort-by sort-by-fn properties)
                     properties)]
    (ui/->table
     {:cell-defaults {:pad 5}
      :rows (for [properties (partition-all columns properties)]
              (for [property properties]
                (try (->overview-property-widget property clicked-id-fn extra-info-text scale)
                     (catch Throwable t
                       (throw (ex-info "" {:property property} t))))))})))

(import 'com.kotcrab.vis.ui.widget.tabbedpane.Tab)
(import 'com.kotcrab.vis.ui.widget.tabbedpane.TabbedPane)
(import 'com.kotcrab.vis.ui.widget.tabbedpane.TabbedPaneAdapter)
(import 'com.kotcrab.vis.ui.widget.VisTable)

(defn- ->tab [{:keys [title content savable? closable-by-user?]}]
  (proxy [Tab] [(boolean savable?) (boolean closable-by-user?)]
    (getTabTitle [] title)
    (getContentTable [] content)))

(defn- ->tabbed-pane [tabs-data]
  (let [main-table (ui/->table {:fill-parent? true})
        container (VisTable.)
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
    (.pad (.left (.add main-table (ui/->label "[LIGHT_GRAY]Left-Shift: Back to Main Menu[]"))) (float 10))
    (doseq [tab-data tabs-data]
      (.add tabbed-pane (->tab tab-data)))
    main-table))

(defn- open-property-editor-window! [context property-id]
  (stage/add-actor! context (->property-editor-window context property-id)))

(defn- ->tabs-data [ctx]
  (for [property-type (sort (property/types))]
    {:title (:title (property/overview property-type))
     :content (->overview-table ctx property-type open-property-editor-window!)}))

(import 'com.badlogic.gdx.scenes.scene2d.InputListener)

(derive :screens/property-editor :screens/stage)
(defcomponent :screens/property-editor
  (->mk [_ ctx]
    {:stage (let [stage (stage/create ctx
                                      [(ui/->background-image ctx)
                                       (->tabbed-pane (->tabs-data ctx))])]
              (.addListener stage (proxy [InputListener] []
                                    (keyDown [event keycode]
                                      (if (= keycode Input$Keys/SHIFT_LEFT)
                                        (do
                                         (swap! app-state screens/change-screen :screens/main-menu)
                                         true)
                                        false))))
              stage)}))

; TODO schemas not checking if that property exists in db...
; https://github.com/damn/core/issues/59

(defcomponent :one-to-many
  (property/->value [[_ property-type]]
    {:schema [:set [:qualified-keyword {:namespace (property/property-type->id-namespace property-type)}]]}))

(defn- one-to-many-schema->linked-property-type [[_set [_qualif_kw {:keys [namespace]}]]]
  (property/ns-k->property-type namespace))

(comment
 (= (one-to-many-schema->linked-property-type [:set [:qualified-keyword {:namespace :items}]])
    :properties/items)
 )

(defmethod property/edn->value :one-to-many [_ property-ids ctx]
  (map (partial build-property ctx) property-ids))

(defcomponent :one-to-one
  (property/->value [[_ property-type]]
    {:schema [:qualified-keyword {:namespace (property/property-type->id-namespace property-type)}]}))

(defn- one-to-one-schema->linked-property-type [[_qualif_kw {:keys [namespace]}]]
  (property/ns-k->property-type namespace))

(comment
 (= (one-to-one-schema->linked-property-type [:qualified-keyword {:namespace :creatures}])
    :properties/creatuers)
 )

(defmethod property/edn->value :one-to-one [_ property-id ctx]
  (build-property ctx property-id))

(defn- add-one-to-many-rows [ctx table property-type property-ids]
  (let [redo-rows (fn [ctx property-ids]
                    (group/clear-children! table)
                    (add-one-to-many-rows ctx table property-type property-ids)
                    (actor/pack-ancestor-window! table))]
    (ui/add-rows!
     table
     [[(ui/->text-button "+"
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
        (let [property (build-property ctx property-id)
              image-widget (ui/->image-widget (property/->image property)
                                              {:id property-id})]
          (actor/add-tooltip! image-widget #(->info-text property %))
          image-widget))
      (for [id property-ids]
        (ui/->text-button "-" #(do (redo-rows % (disj property-ids id)) %)))])))

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
         (ui/->text-button "+"
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
         (let [property (build-property ctx property-id)
               image-widget (ui/->image-widget (property/->image property)
                                               {:id property-id})]
           (actor/add-tooltip! image-widget #(->info-text property %))
           image-widget))]
      [(when property-id
         (ui/->text-button "-" #(do (redo-rows % nil) %)))]])))

(defmethod property/->widget :one-to-one [[_ data] property-id ctx]
  (let [table (ui/->table {:cell-defaults {:pad 5}})]
    (add-one-to-one-rows ctx
                         table
                         (one-to-one-schema->linked-property-type (:schema data))
                         property-id)
    table))

(defmethod property/widget->value :one-to-one [_ widget]
  (->> (group/children widget) (keep actor/id) first))
