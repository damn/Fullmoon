(ns core.editor
  (:require [clojure.edn :as edn]
            [clojure.set :as set]
            [clojure.string :as str]
            [component.core :as component]
            [component.info :as info]
            [component.schema :as schema]
            [core.db :as db]
            [core.property :as property]
            [core.widgets.error :refer [error-window!]]
            [gdx.assets :as assets]
            [gdx.graphics :as g]
            [gdx.input :refer [key-just-pressed?]]
            [gdx.ui :as ui]
            [gdx.ui.actor :as a]
            [gdx.ui.stage-screen :as stage-screen :refer [stage-add!]]
            [gdx.screen :as screen]
            [malli.core :as m]
            [malli.generator :as mg]
            [utils.core :refer [safe-get index-of]]))

(defn- widget-type [schema _]
  (let [stype (schema/type schema)]
    (cond
     (#{:map-optional :components-ns}                 stype) :map
     (#{:number :nat-int :int :pos :pos-int :val-max} stype) :number
     :else stype)))

(defmulti ^:private ->widget      widget-type)
(defmulti ^:private widget->value widget-type)

(comment
 (keys (methods ->widget))
 (:enum :sound :number :default :string :one-to-many :image :one-to-one :boolean :map :schema/animation)
 )

(defmethod ->widget :schema/animation [_ animation]
  (ui/table {:rows [(for [image (:frames animation)]
                      (ui/image->widget (g/edn->image image) {}))]
             :cell-defaults {:pad 1}}))

(defn- add-schema-tooltip! [widget schema]
  (ui/add-tooltip! widget (str schema))
  widget)

(defn- ->edn-str [v]
  (binding [*print-level* nil]
    (pr-str v)))

(defmethod ->widget :boolean [_ checked?]
  (assert (boolean? checked?))
  (ui/check-box "" (fn [_]) checked?))

(defmethod widget->value :boolean [_ widget]
  (.isChecked ^com.kotcrab.vis.ui.widget.VisCheckBox widget))

(defmethod ->widget :string [schema v]
  (add-schema-tooltip! (ui/text-field v {}) schema))

(defmethod widget->value :string [_ widget]
  (.getText ^com.kotcrab.vis.ui.widget.VisTextField widget))

(defmethod ->widget :number [schema v]
  (add-schema-tooltip! (ui/text-field (->edn-str v) {}) schema))

(defmethod widget->value :number [_ widget]
  (edn/read-string (.getText ^com.kotcrab.vis.ui.widget.VisTextField widget)))

(defmethod ->widget :enum [schema v]
  (ui/select-box {:items (map ->edn-str (rest schema))
                  :selected (->edn-str v)}))

(defmethod widget->value :enum [_ widget]
  (edn/read-string (.getSelected ^com.kotcrab.vis.ui.widget.VisSelectBox widget)))

(defmethod db/edn->value :image [_ image]
  (g/edn->image image))

; too many ! too big ! scroll ... only show files first & preview?
; make tree view from folders, etc. .. !! all creatures animations showing...
(defn- texture-rows []
  (for [file (sort assets/all-texture-files)]
    [(ui/image-button (g/image file) (fn []))]
    #_[(ui/text-button file (fn []))]))

(defmethod ->widget :image [_ image]
  (ui/image->widget (g/edn->image image) {})
  #_(ui/image-button image
                     #(stage-add! (->scrollable-choose-window (texture-rows)))
                     {:dimensions [96 96]})) ; x2  , not hardcoded here

; TODO set to preferred width/height ??? why layouting not working properly?
; use a tree?
; make example with plain data
(defn- ->scroll-pane-cell [rows]
  (let [table (ui/table {:rows rows :cell-defaults {:pad 1} :pack? true})
        scroll-pane (ui/scroll-pane table)]
    {:actor scroll-pane
     :width  (- (g/gui-viewport-width)  600)    ; (+ (actor/width table) 200)
     :height (- (g/gui-viewport-height) 100)})) ; (min (- (g/gui-viewport-height) 50) (actor/height table))

(defn- ->scrollable-choose-window [rows]
  (ui/window {:title "Choose"
              :modal? true
              :close-button? true
              :center? true
              :close-on-escape? true
              :rows [[(->scroll-pane-cell rows)]]
              :pack? true}))

(defn- ->play-sound-button [sound-file]
  (ui/text-button "play!" #(assets/play-sound! sound-file)))

(declare ->sound-columns)

(defn- open-sounds-window! [table]
  (let [rows (for [sound-file assets/all-sound-files]
               [(ui/text-button (str/replace-first sound-file "sounds/" "")
                                (fn []
                                  (ui/clear-children! table)
                                  (ui/add-rows! table [(->sound-columns table sound-file)])
                                  (a/remove! (ui/find-ancestor-window ui/*on-clicked-actor*))
                                  (ui/pack-ancestor-window! table)
                                  (a/set-id! table sound-file)))
                (->play-sound-button sound-file)])]
    (stage-add! (->scrollable-choose-window rows))))

(defn- ->sound-columns [table sound-file]
  [(ui/text-button (name sound-file) #(open-sounds-window! table))
   (->play-sound-button sound-file)])

(defmethod ->widget :sound [_ sound-file]
  (let [table (ui/table {:cell-defaults {:pad 5}})]
    (ui/add-rows! table [(if sound-file
                           (->sound-columns table sound-file)
                           [(ui/text-button "No sound" #(open-sounds-window! table))])])
    table))

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

(defn- truncate [s limit]
  (if (> (count s) limit)
    (str (subs s 0 limit) "...")
    s))

(defmethod ->widget :default [_ v]
  (ui/label (truncate (->edn-str v) 60)))

(defmethod widget->value :default [_ widget]
  (a/id widget))

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
     (#{:one-to-one :one-to-many} schema) nil
     ;(#{:map} type) {} ; cannot have empty for required keys, then no Add Component button
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

(defmethod ->widget :map [schema m]
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


(defmethod widget->value :map [_ table]
  (attribute-widget-group->data (:attribute-widget-group table)))

(defn- ->attribute-label [k]
  (let [label (ui/label (str k))]
    (when-let [doc (:editor/doc (component/meta k))]
      (ui/add-tooltip! label doc))
    label))

(defn- ->component-widget [[k k-props v] & {:keys [horizontal-sep?]}]
  (let [label (->attribute-label k)
        value-widget (->widget (schema/of k) v)
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
             [k (widget->value (schema/of k) value-widget)])))

;;

(defn- apply-context-fn [window f]
  (fn []
    (try
     (f)
     (a/remove! window)
     (catch Throwable t
       (error-window! t)))))

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
    (ui/add-rows! window [[(->scroll-pane-cell [[{:actor widgets :colspan 2}]
                                                [(ui/text-button "Save [LIGHT_GRAY](ENTER)[]" save!)
                                                 (ui/text-button "Delete" delete!)]])]])
    (ui/add-actor! window (ui/actor {:act (fn []
                                            (when (key-just-pressed? :enter)
                                              (save!)))}))
    (.pack window)
    window))

(defn- ->overview-property-widget [{:keys [property/id] :as props} clicked-id-fn extra-info-text scale]
  (let [on-clicked #(clicked-id-fn id)
        button (if-let [image (property/->image props)]
                 (ui/image-button image on-clicked {:scale scale})
                 (ui/text-button (name id) on-clicked))
        top-widget (ui/label (or (and extra-info-text (extra-info-text props)) ""))
        stack (ui/stack [button top-widget])]
    (ui/add-tooltip! button #(info/->text props))
    (a/set-touchable! top-widget :disabled)
    stack))

(defn- ->overview-table [property-type clicked-id-fn]
  (let [{:keys [sort-by-fn
                extra-info-text
                columns
                image/scale]} (property/overview property-type)
        properties (db/all property-type)
        properties (if sort-by-fn
                     (sort-by sort-by-fn properties)
                     properties)]
    (ui/table
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
  (let [main-table (ui/table {:fill-parent? true})
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
    (.pad (.left (.add main-table (ui/label "[LIGHT_GRAY]Left-Shift: Back to Main Menu[]"))) (float 10))
    (doseq [tab-data tabs-data]
      (.add tabbed-pane (->tab tab-data)))
    main-table))

(defn- open-property-editor-window! [property-id]
  (stage-add! (->property-editor-window property-id)))

(defn- ->tabs-data []
  (for [property-type (sort (property/types))]
    {:title (:title (property/overview property-type))
     :content (->overview-table property-type open-property-editor-window!)}))

(defn screen [->background-image]
  [:screens/property-editor
   (stage-screen/create :actors
                        [(->background-image)
                         (->tabbed-pane (->tabs-data))
                         (ui/actor {:act (fn []
                                           (when (key-just-pressed? :shift-left)
                                             (screen/change! :screens/main-menu)))})])])

; TODO schemas not checking if that property exists in db...
; https://github.com/damn/core/issues/59

(defn- add-one-to-many-rows [table property-type property-ids]
  (let [redo-rows (fn [property-ids]
                    (ui/clear-children! table)
                    (add-one-to-many-rows table property-type property-ids)
                    (ui/pack-ancestor-window! table))]
    (ui/add-rows!
     table
     [[(ui/text-button "+"
                       (fn []
                         (let [window (ui/window {:title "Choose"
                                                  :modal? true
                                                  :close-button? true
                                                  :center? true
                                                  :close-on-escape? true})
                               clicked-id-fn (fn [id]
                                               (a/remove! window)
                                               (redo-rows (conj property-ids id)))]
                           (.add window (->overview-table property-type clicked-id-fn))
                           (.pack window)
                           (stage-add! window))))]
      (for [property-id property-ids]
        (let [property (db/get property-id)
              image-widget (ui/image->widget (property/->image property) {:id property-id})]
          (ui/add-tooltip! image-widget #(info/->text property))
          image-widget))
      (for [id property-ids]
        (ui/text-button "-" #(redo-rows (disj property-ids id))))])))

(defmethod ->widget :one-to-many [[_ property-type] property-ids]
  (let [table (ui/table {:cell-defaults {:pad 5}})]
    (add-one-to-many-rows table property-type property-ids)
    table))

(defmethod widget->value :one-to-many [_ widget]
  (->> (ui/children widget)
       (keep a/id)
       set))

(defn- add-one-to-one-rows [table property-type property-id]
  (let [redo-rows (fn [id]
                    (ui/clear-children! table)
                    (add-one-to-one-rows table property-type id)
                    (ui/pack-ancestor-window! table))]
    (ui/add-rows!
     table
     [[(when-not property-id
         (ui/text-button "+"
                         (fn []
                           (let [window (ui/window {:title "Choose"
                                                    :modal? true
                                                    :close-button? true
                                                    :center? true
                                                    :close-on-escape? true})
                                 clicked-id-fn (fn [id]
                                                 (a/remove! window)
                                                 (redo-rows id))]
                             (.add window (->overview-table property-type clicked-id-fn))
                             (.pack window)
                             (stage-add! window)))))]
      [(when property-id
         (let [property (db/get property-id)
               image-widget (ui/image->widget (property/->image property) {:id property-id})]
           (ui/add-tooltip! image-widget #(info/->text property))
           image-widget))]
      [(when property-id
         (ui/text-button "-" #(redo-rows nil)))]])))

(defmethod ->widget :one-to-one [[_ property-type] property-id]
  (let [table (ui/table {:cell-defaults {:pad 5}})]
    (add-one-to-one-rows table property-type property-id)
    table))

(defmethod widget->value :one-to-one [_ widget]
  (->> (ui/children widget) (keep a/id) first))
