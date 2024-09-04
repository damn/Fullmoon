(ns components.screens.property-editor
  (:require [clojure.set :as set]
            [malli.core :as m]
            [malli.generator :as mg]
            [gdx.input :as input]
            [gdx.input.keys :as input.keys]
            [utils.core :as utils :refer [index-of]]
            [core.component :refer [defcomponent] :as component]
            [core.components :as components]
            [core.property :as property]
            [core.context :as ctx]
            [core.data :as data]
            [core.scene2d.actor :as actor]
            [core.scene2d.group :as group]
            [core.scene2d.ui.table :refer [add! add-rows! cells ->horizontal-separator-cell ->vertical-separator-cell]]
            [core.scene2d.ui.cell :refer [set-actor!]]
            [core.scene2d.ui.widget-group :refer [pack!]]))

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

(defn- data->default-value [[data-type {:keys [schema]}]]
  (cond
   (#{:one-to-one :one-to-many} data-type) nil
   ;(#{:map} data-type) {}
   :else (mg/generate schema {:size 3})))

(defn- ->choose-component-window [data attribute-widget-group]
  (fn [ctx]
    (let [k-props (k-properties (:schema data))
          window (ctx/->window ctx {:title "Choose"
                                    :modal? true
                                    :close-button? true
                                    :center? true
                                    :close-on-escape? true
                                    :cell-defaults {:pad 5}})
          remaining-ks (sort (remove (set (keys (attribute-widget-group->data attribute-widget-group)))
                                              (map-keys (:schema data))))]
      (add-rows! window (for [k remaining-ks]
                          [(ctx/->text-button ctx
                                              (name k)
                                              (fn [ctx]
                                                (actor/remove! window)
                                                (group/add-actor! attribute-widget-group
                                                                  (->component-widget ctx
                                                                                      [k (get k-props k) (data->default-value data)]
                                                                                      :horizontal-sep?
                                                                                      (pos? (count (group/children attribute-widget-group)))))
                                                (actor/pack-ancestor-window! attribute-widget-group)
                                                ctx))]))
      (pack! window)
      (ctx/add-to-stage! ctx window))))

(declare ->attribute-widget-group)

(defn- optional-keyset [schema]
  (set (map first
            (filter (fn [[k prop-m]] (:optional prop-m))
                    (k-properties schema)))))

(defmethod data/->widget :map [[_ data] m ctx]
  (let [attribute-widget-group (->attribute-widget-group ctx (:schema data) m)
        optional-keys-left? (seq (set/difference (optional-keyset (:schema data))
                                                 (set (keys m))))]
    (actor/set-id! attribute-widget-group :attribute-widget-group)
    (ctx/->table ctx {:cell-defaults {:pad 5}
                      :rows (remove nil?
                                    [(when optional-keys-left?
                                       [(ctx/->text-button
                                         ctx
                                         "Add component"
                                         (->choose-component-window data attribute-widget-group))])
                                     (when optional-keys-left?
                                       [(->horizontal-separator-cell 1)])
                                     [attribute-widget-group]])})))


(defmethod data/widget->value :map [_ table]
  (attribute-widget-group->data (:attribute-widget-group table)))

(defn- ->attribute-label [ctx k]
  (let [label (ctx/->label ctx (str k))]
    (when-let [doc (component/doc k)]
      (actor/add-tooltip! label doc))
    label))

(defn ->component-widget [ctx [k k-props v] & {:keys [horizontal-sep?]}]
  (let [label (->attribute-label ctx k)
        value-widget (data/->widget (component/data-component k) v ctx)
        table (ctx/->table ctx {:id k
                                :cell-defaults {:pad 4}})
        column (remove nil?
                       [(when (:optional k-props)
                          (ctx/->text-button ctx "-" (fn [ctx]
                                                       (let [window (actor/find-ancestor-window table)]
                                                         (actor/remove! table)
                                                         (pack! window))
                                                       ctx)))
                        label
                        (->vertical-separator-cell)
                        value-widget])
        rows [(when horizontal-sep? [(->horizontal-separator-cell (count column))])
              column]]
    (actor/set-id! value-widget v)
    (add-rows! table (remove nil? rows))
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
  (ctx/->vertical-group ctx (->component-widgets ctx schema props)))

(defn- attribute-widget-group->data [group]
  (into {} (for [k (map actor/id (group/children group))
                 :let [table (k group)
                       value-widget (attribute-widget-table->value-widget table)]]
             [k (data/widget->value (component/data-component k) value-widget)])))

;;

(defn- apply-context-fn [window f]
  (fn [ctx]
    (try
     (let [ctx (f ctx)]
       (actor/remove! window)
       ctx)
     (catch Throwable t
       (ctx/error-window! ctx t)))))

(defn- ->property-editor-window [ctx id]
  (let [props (ctx/property ctx id)
        window (ctx/->window ctx {:title "Edit Property"
                                  :modal? true
                                  :close-button? true
                                  :center? true
                                  :close-on-escape? true
                                  :cell-defaults {:pad 5}})
        widgets (->attribute-widget-group ctx (ctx/property->schema ctx props) props)
        save!   (apply-context-fn window #(ctx/update! % (attribute-widget-group->data widgets)))
        delete! (apply-context-fn window #(ctx/delete! % id))]
    (add-rows! window [[(data/->scroll-pane-cell ctx [[{:actor widgets :colspan 2}]
                                                      [(ctx/->text-button ctx "Save [LIGHT_GRAY](ENTER)[]" save!)
                                                       (ctx/->text-button ctx "Delete" delete!)]])]])
    (group/add-actor! window
                      (ctx/->actor ctx {:act (fn [{:keys [context/state]}]
                                               (when (input/key-just-pressed? input.keys/enter)
                                                 (swap! state save!)))}))
    (pack! window)
    window))

(defn- ->overview-property-widget [{:keys [property/id] :as props} ctx clicked-id-fn extra-info-text scale]
  (let [on-clicked #(clicked-id-fn % id)
        button (if-let [image (property/->image props)]
                 (ctx/->image-button ctx image on-clicked {:scale scale})
                 (ctx/->text-button ctx (name id) on-clicked))
        top-widget (ctx/->label ctx (or (and extra-info-text (extra-info-text props)) ""))
        stack (ctx/->stack ctx [button top-widget])]
    (do
     (actor/add-tooltip! button #(components/info-text props %))
     (actor/set-touchable! top-widget :disabled)
     stack)))

(extend-type core.context.Context
  core.context/PropertyEditor
  (->overview-table [ctx property-type clicked-id-fn]
    (let [{:keys [title
                  sort-by-fn
                  extra-info-text
                  columns
                  image/scale]} (ctx/overview ctx property-type)
          properties (ctx/all-properties ctx property-type)
          properties (if sort-by-fn
                       (sort-by sort-by-fn properties)
                       properties)]
      (ctx/->table ctx
                   {:cell-defaults {:pad 5}
                    :rows (for [properties (partition-all columns properties)]
                            (for [property properties]
                              (try (->overview-property-widget property ctx clicked-id-fn extra-info-text scale)
                                   (catch Throwable t
                                     (throw (ex-info "" {:property property} t))))))}))))

(import 'com.kotcrab.vis.ui.widget.tabbedpane.Tab)
(import 'com.kotcrab.vis.ui.widget.tabbedpane.TabbedPane)
(import 'com.kotcrab.vis.ui.widget.tabbedpane.TabbedPaneAdapter)
(import 'com.kotcrab.vis.ui.widget.VisTable)

(defn- ->tab [{:keys [title content savable?  closable-by-user?]}]
  (proxy [Tab] [(boolean savable?) (boolean closable-by-user?)]
    (getTabTitle [] title)
    (getContentTable [] content)))

(defn- ->tabbed-pane [ctx tabs-data]
  (let [main-table (ctx/->table ctx {:fill-parent? true})
        container (VisTable.)
        tabbed-pane (TabbedPane.)]
    (.addListener tabbed-pane
                  (proxy [TabbedPaneAdapter] []
                    (switchedTab [tab]
                      (.clearChildren container)
                      (.fill (.expand (.add container (.getContentTable tab)))))))
    (.fillX (.expandX (.add main-table (.getTable tabbed-pane))))
    (.row main-table)
    (.fill (.expand (.add main-table container)))
    (.row main-table)
    (.pad (.left (.add main-table (ctx/->label ctx "[LIGHT_GRAY]Left-Shift: Back to Main Menu[]"))) (float 10))
    (doseq [tab-data tabs-data]
      (.add tabbed-pane (->tab tab-data)))
    main-table))

(defn- open-property-editor-window! [context property-id]
  (ctx/add-to-stage! context (->property-editor-window context property-id)))

(defn- ->tabs-data [ctx]
  (for [property-type (ctx/property-types ctx)]
    {:title (:title (ctx/overview ctx property-type))
     :content (ctx/->overview-table ctx property-type open-property-editor-window!)}))

(import 'com.badlogic.gdx.scenes.scene2d.InputListener)

(derive :screens/property-editor :screens/stage-screen)
(defcomponent :screens/property-editor
  (component/create [_ {:keys [context/state] :as ctx}]
    {:stage (let [stage (ctx/->stage ctx
                         [(ctx/->background-image ctx)
                          (->tabbed-pane ctx (->tabs-data ctx))])]
              (.addListener stage (proxy [InputListener] []
                                    (keyDown [event keycode]
                                      (if (= keycode gdx.input.keys/shift-left)
                                        (do
                                         (swap! state ctx/change-screen :screens/main-menu)
                                         true)
                                        false))))
              stage)}))
