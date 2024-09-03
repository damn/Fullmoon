(ns components.screens.property-editor
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [malli.core :as m]
            [gdx.input :as input]
            [gdx.input.keys :as input.keys]
            [utils.core :as utils :refer [index-of]]
            [core.component :refer [defcomponent] :as component]
            [core.components :as components]
            core.property
            [core.context :as ctx]
            [core.data :as data]
            [core.scene2d.actor :as actor]
            [core.scene2d.group :as group]
            [core.scene2d.ui.text-field :as text-field]
            [core.scene2d.ui.table :refer [add! add-rows! cells ->horizontal-separator-cell ->vertical-separator-cell]]
            [core.scene2d.ui.cell :refer [set-actor!]]
            [core.scene2d.ui.widget-group :refer [pack!]]))

(comment
 ; edit ingame
 ; cursor not changing becuz manual update
 (open-property-editor-window! @app/state (:property/id (ctx/mouseover-entity* @app/state)))
 )

; TODO save button show if changes made, otherwise disabled?
; when closing (lose changes? yes no)
; TODO overview table not refreshed after changes in property editor window

(defn- ->scroll-pane-cell [ctx rows]
  (let [table (ctx/->table ctx {:rows rows
                                :cell-defaults {:pad 1}
                                :pack? true})
        scroll-pane (ctx/->scroll-pane ctx table)]
    {:actor scroll-pane
     :width (- (ctx/gui-viewport-width ctx) 600) ;(+ (actor/width table) 200)
     :height (- (ctx/gui-viewport-height ctx) 100) })) ;(min (- (ctx/gui-viewport-height ctx) 50) (actor/height table))

; TODO set to preferred width/height ??? why layouting not working properly?
; use a tree?
; make example with plain data

(defn ->scrollable-choose-window [ctx rows]
  (ctx/->window ctx {:title "Choose"
                     :modal? true
                     :close-button? true
                     :center? true
                     :close-on-escape? true
                     :rows [[(->scroll-pane-cell ctx rows)]]
                     :pack? true}))

(defn- add-schema-tooltip! [widget data]
  (actor/add-tooltip! widget (str "Schema: " (pr-str (m/form (:schema data)))))
  widget)

(defmethod data/->widget :string [[_ data] v ctx]
  (add-schema-tooltip! (ctx/->text-field ctx v {})
                       data))

(defmethod data/widget->value :string [_ widget]
  (text-field/text widget))

(defmethod data/->widget :number [[_ data] v ctx]
  (add-schema-tooltip! (ctx/->text-field ctx (utils/->edn-str v) {})
                       data))

(defmethod data/widget->value :number [_ widget]
  (edn/read-string (text-field/text widget)))

;;

(defmethod data/->widget :boolean [_ checked? ctx]
  (assert (boolean? checked?))
  (ctx/->check-box ctx "" (fn [_]) checked?))

(defmethod data/widget->value :boolean [_ widget]
  (.isChecked ^com.kotcrab.vis.ui.widget.VisCheckBox widget))

;;

(defmethod data/->widget :enum [[_ data] v ctx]
  (ctx/->select-box ctx {:items (map utils/->edn-str (:items data))
                         :selected (utils/->edn-str v)}))

(defmethod data/widget->value :enum [_ widget]
  (edn/read-string (.getSelected ^com.kotcrab.vis.ui.widget.VisSelectBox widget)))

;;

; TODO too many ! too big ! scroll ... only show files first & preview?
; TODO make tree view from folders, etc. .. !! all creatures animations showing...
(defn- texture-rows [ctx]
  (for [file (sort (ctx/all-texture-files ctx))]
    [(ctx/->image-button ctx (ctx/create-image ctx file) identity)]
    #_[(ctx/->text-button ctx file identity)]))

(defmethod data/->widget :image [_ image ctx]
  (ctx/->image-widget ctx image {})
  #_(ctx/->image-button ctx image
                        #(ctx/add-to-stage! % (->scrollable-choose-window % (texture-rows %)))
                        {:dimensions [96 96]})) ; x2  , not hardcoded here TODO

;;

; looping? - click on widget restart
; frame-duration
; frames ....
; hidden actor act tick atom animation & set current frame image drawable
(defmethod data/->widget :animation [_ animation ctx]
  (ctx/->table ctx {:rows [(for [image (:frames animation)]
                             (ctx/->image-widget ctx image {}))]
                    :cell-defaults {:pad 1}}))

;;

(declare ->attribute-widget-table
         attribute-widget-group->data)

(defn- ->add-component-button [data attribute-widget-group ctx]
  (ctx/->text-button
   ctx
   "Add component"
   (fn [ctx]
     (let [window (ctx/->window ctx {:title "Choose"
                                     :modal? true
                                     :close-button? true
                                     :center? true
                                     :close-on-escape? true
                                     :cell-defaults {:pad 5}})]
       (add-rows! window (for [nested-k (sort (remove (set (keys (attribute-widget-group->data attribute-widget-group)))
                                                      (:components data)))]
                           [(ctx/->text-button ctx (name nested-k)
                                               (fn [ctx]
                                                 (actor/remove! window)
                                                 (group/add-actor! attribute-widget-group
                                                                   (->attribute-widget-table ctx
                                                                                             [nested-k (component/default-value nested-k)]
                                                                                             :horizontal-sep?
                                                                                             (pos? (count (group/children attribute-widget-group)))))
                                                 (actor/pack-ancestor-window! attribute-widget-group)
                                                 ctx))]))
       (pack! window)
       (ctx/add-to-stage! ctx window)))))

(declare ->attribute-widget-group)

(defmethod data/->widget :map [[_ data] m ctx]
  (let [attribute-widget-group (->attribute-widget-group ctx m)]
    (actor/set-id! attribute-widget-group :attribute-widget-group)
    (ctx/->table ctx {:cell-defaults {:pad 5}
                      :rows (remove nil?
                                    [(when (:components data)
                                       [(->add-component-button data attribute-widget-group ctx)])
                                     (when (:components data)
                                       [(->horizontal-separator-cell 1)])
                                     [attribute-widget-group]])})))


(defmethod data/widget->value :map [_ table]
  (attribute-widget-group->data (:attribute-widget-group table)))

;;

(defn- ->play-sound-button [ctx sound-file]
  (ctx/->text-button ctx "play!" #(do (ctx/play-sound! % sound-file) %)))

(declare ->sound-columns)

(defn- open-sounds-window! [ctx table]
  (let [rows (for [sound-file (ctx/all-sound-files ctx)]
               [(ctx/->text-button ctx
                                   (str/replace-first sound-file "sounds/" "")
                                   (fn [{:keys [context/actor] :as ctx}]
                                     (group/clear-children! table)
                                     (add-rows! table [(->sound-columns ctx table sound-file)])
                                     (actor/remove! (actor/find-ancestor-window actor))
                                     (actor/pack-ancestor-window! table)
                                     (actor/set-id! table sound-file)
                                     ctx))
                (->play-sound-button ctx sound-file)])]
    (ctx/add-to-stage! ctx (->scrollable-choose-window ctx rows))))

(defn- ->sound-columns [ctx table sound-file]
  [(ctx/->text-button ctx (name sound-file) #(open-sounds-window! % table))
   (->play-sound-button ctx sound-file)])

(defmethod data/->widget :sound [_ sound-file ctx]
  (let [table (ctx/->table ctx {:cell-defaults {:pad 5}})]
    (add-rows! table [(if sound-file
                        (->sound-columns ctx table sound-file)
                        [(ctx/->text-button ctx "No sound" #(open-sounds-window! % table))])])
    table))

;;

(declare ->overview-table)

(defn- add-one-to-many-rows [ctx table property-type properties]
  (let [redo-rows (fn [ctx property-ids]
                    (group/clear-children! table)
                    (add-one-to-many-rows ctx table property-type (map #(ctx/property ctx %) property-ids))
                    (actor/pack-ancestor-window! table))
        property-ids (set (map :property/id properties))]
    (add-rows! table
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
                                        (add! window (->overview-table ctx property-type clicked-id-fn))
                                        (pack! window)
                                        (ctx/add-to-stage! ctx window))))]
                (for [property properties]
                  (let [image-widget (ctx/->image-widget ctx ; image-button/link?
                                                         (core.property/property->image property)
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

;;

(defn- add-one-to-one-rows [ctx table property-type property]
  (let [redo-rows (fn [ctx id]
                    (group/clear-children! table)
                    (add-one-to-one-rows ctx table property-type (when id (ctx/property ctx id)))
                    (actor/pack-ancestor-window! table))]
    (add-rows! table
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
                                          (add! window (->overview-table ctx property-type clicked-id-fn))
                                          (pack! window)
                                          (ctx/add-to-stage! ctx window)))))]
                [(when property
                   (let [image-widget (ctx/->image-widget ctx ; image-button/link?
                                                          (core.property/property->image property)
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

;;

(defn- ->attribute-label [ctx k]
  (let [label (ctx/->label ctx (str k))]
    (when-let [doc (component/doc k)]
      (actor/add-tooltip! label doc))
    label))

(defn ->attribute-widget-table [ctx [k v] & {:keys [horizontal-sep?]}]
  (let [label (->attribute-label ctx k)
        value-widget (data/->widget (component/data-component k) v ctx)
        table (ctx/->table ctx {:id k
                                :cell-defaults {:pad 4}})
        column (remove nil?
                       [(when (component/optional? k)
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

(let [k-sort-order [:property/id
                    :app/lwjgl3
                    :entity/image
                    :entity/animation
                    :property/pretty-name
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
                    :skill/cooldown]]
  (defn- sort-attributes [props]
    (sort-by #(or (index-of (first %) k-sort-order) 99)
             props)))

(defn- ->attribute-widget-tables [ctx props]
  (let [first-row? (atom true)]
    (for [[k v] (sort-attributes props)
          :let [sep? (not @first-row?)
                _ (reset! first-row? false)]]
      (->attribute-widget-table ctx [k v] :horizontal-sep? sep?))))

(defn- ->attribute-widget-group [ctx props]
  (ctx/->vertical-group ctx (->attribute-widget-tables ctx props)))

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

(defn- ->property-editor-window [context id]
  (let [props (ctx/property context id)
        window (ctx/->window context {:title "Edit Property"
                                      :modal? true
                                      :close-button? true
                                      :center? true
                                      :close-on-escape? true
                                      :cell-defaults {:pad 5}})
        widgets (->attribute-widget-group context props)
        save!   (apply-context-fn window #(ctx/update! % (attribute-widget-group->data widgets)))
        delete! (apply-context-fn window #(ctx/delete! % id))]
    (add-rows! window [[(->scroll-pane-cell context [[{:actor widgets :colspan 2}]
                                                     [(ctx/->text-button context "Save [LIGHT_GRAY](ENTER)[]" save!)
                                                      (ctx/->text-button context "Delete" delete!)]])]])
    (group/add-actor! window
                      (ctx/->actor context {:act (fn [{:keys [context/state]}]
                                                   (when (input/key-just-pressed? input.keys/enter)
                                                     (swap! state save!)))}))
    (pack! window)
    window))

;;

(defn- ->overview-property-widget [{:keys [property/id] :as props} ctx clicked-id-fn extra-info-text scale]
  (let [on-clicked #(clicked-id-fn % id)
        button (if-let [image (core.property/property->image props)]
                 (ctx/->image-button ctx image on-clicked {:scale scale})
                 (ctx/->text-button ctx (name id) on-clicked))
        top-widget (ctx/->label ctx (or (and extra-info-text (extra-info-text props)) ""))
        stack (ctx/->stack ctx [button top-widget])]
    (do
     (actor/add-tooltip! button #(components/info-text props %))
     (actor/set-touchable! top-widget :disabled)
     stack)))

(defn- ->overview-table
  "Creates a table with all-properties of property-type and buttons for each id
  which on-clicked calls clicked-id-fn."
  [ctx property-type clicked-id-fn]
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
                  :rows (for [properties (partition-all columns properties)] ; TODO can just do 1 for?
                          (for [property properties]
                            (try (->overview-property-widget property ctx clicked-id-fn extra-info-text scale)
                                 (catch Throwable t
                                   (throw (ex-info "" {:property property} t))))))})))

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
     :content (->overview-table ctx property-type open-property-editor-window!)}))

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
