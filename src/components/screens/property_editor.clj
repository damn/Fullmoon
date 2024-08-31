(ns components.screens.property-editor
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [malli.core :as m]
            [gdx.input :as input]
            [gdx.input.keys :as input.keys]
            [utils.core :refer [index-of]]
            [core.component :refer [defcomponent] :as component]
            [core.components :as components]
            core.property
            [core.context :as ctx :refer [get-stage ->text-button ->image-button ->label ->text-field ->image-widget ->table ->stack ->window all-sound-files play-sound! ->vertical-group ->check-box ->select-box ->actor add-to-stage! ->scroll-pane]]
            [core.scene2d.actor :as actor :refer [remove! set-touchable! parent add-tooltip! find-ancestor-window pack-ancestor-window!]]
            [core.scene2d.group :refer [add-actor! clear-children! children]]
            [core.scene2d.ui.text-field :as text-field]
            [core.scene2d.ui.table :refer [add! add-rows! cells ->horizontal-separator-cell ->vertical-separator-cell]]
            [core.scene2d.ui.cell :refer [set-actor!]]
            [core.scene2d.ui.widget-group :refer [pack!]]))

; TODO save button show if changes made, otherwise disabled?
; when closing (lose changes? yes no)
; TODO overview table not refreshed after changes in property editor window

(defn- ->scroll-pane-cell [ctx rows]
  (let [table (->table ctx {:rows rows
                            :cell-defaults {:pad 1}
                            :pack? true})
        scroll-pane (->scroll-pane ctx table)]
    {:actor scroll-pane
     :width (- (ctx/gui-viewport-width ctx) 600) ;(+ (actor/width table) 200)
     :height (- (ctx/gui-viewport-height ctx) 100) })) ;(min (- (ctx/gui-viewport-height ctx) 50) (actor/height table))

; TODO set to preferred width/height ??? why layouting not working properly?
; use a tree?
; make example with plain data

(defn ->scrollable-choose-window [ctx rows]
  (->window ctx {:title "Choose"
                 :modal? true
                 :close-button? true
                 :center? true
                 :close-on-escape? true
                 :rows [[(->scroll-pane-cell ctx rows)]]
                 :pack? true}))

(defmulti ->value-widget     (fn [data _v _ctx] (:widget data)))
(defmulti value-widget->data (fn [data _widget] (:widget data)))

(defmethod value-widget->data :default [_ widget]
  (actor/id widget))

;;

(defn ->edn [v]
  (binding [*print-level* nil]
    (pr-str v)))

(defmethod ->value-widget :label [_ v ctx]
  (->label ctx (->edn v)))

;;

(defmethod ->value-widget :string-text-field [data v ctx]
  (let [widget (->text-field ctx v {})]
    (add-tooltip! widget (str "Schema: " (pr-str (m/form (:schema data)))))
    widget))

(defmethod value-widget->data :string-text-field [_ widget]
  (text-field/text widget))

(defmethod ->value-widget :number-text-field [data v ctx]
  (let [widget (->text-field ctx (->edn v) {})]
    (add-tooltip! widget (str "Schema: " (pr-str (m/form (:schema data)))))
    widget))

(defmethod value-widget->data :number-text-field [_ widget]
  (edn/read-string (text-field/text widget)))

;;

(defmethod ->value-widget :check-box [_ checked? ctx]
  (assert (boolean? checked?))
  (->check-box ctx "" (fn [_]) checked?))

(defmethod value-widget->data :check-box [_ widget]
  (.isChecked ^com.kotcrab.vis.ui.widget.VisCheckBox widget))

;;

(defmethod ->value-widget :enum [data v ctx]
  (->select-box ctx {:items (map ->edn (:items data))
                     :selected (->edn v)}))

(defmethod value-widget->data :enum [_ widget]
  (edn/read-string (.getSelected ^com.kotcrab.vis.ui.widget.VisSelectBox widget)))

;;

; TODO too many ! too big ! scroll ... only show files first & preview?
; TODO make tree view from folders, etc. .. !! all creatures animations showing...
(defn- texture-rows [ctx]
  (for [file (sort (core.context/all-texture-files ctx))]
    [(->image-button ctx (core.context/create-image ctx file) identity)]
    #_[(->text-button ctx file identity)]))

(defmethod ->value-widget :image [_ image ctx]
  (->image-widget ctx image {})
  #_(->image-button ctx image
                  #(do (add-to-stage! % (->scrollable-choose-window % (texture-rows %))) %)
                  {:dimensions [96 96]})) ; x2  , not hardcoded here TODO

;;

; looping? - click on widget restart
; frame-duration
; frames ....
; hidden actor act tick atom animation & set current frame image drawable
(defmethod ->value-widget :animation [_ animation ctx]
  (->table ctx {:rows [(for [image (:frames animation)]
                         (->image-widget ctx image {}))]
                :cell-defaults {:pad 1}}))

;;

(declare ->attribute-widget-table
         attribute-widget-group->data)

(defn- ->add-nested-map-button [data attribute-widget-group ctx]
  (->text-button
   ctx
   "Add component"
   (fn [ctx]
     (let [window (->window ctx {:title "Choose"
                                 :modal? true
                                 :close-button? true
                                 :center? true
                                 :close-on-escape? true
                                 :cell-defaults {:pad 5}})]
       (add-rows! window (for [nested-k (sort (remove (set (keys (attribute-widget-group->data attribute-widget-group)))
                                                      (:components data)))]
                           [(->text-button ctx (name nested-k)
                                           (fn [ctx]
                                             (remove! window)
                                             (add-actor! attribute-widget-group
                                                         (->attribute-widget-table ctx
                                                                                   [nested-k (component/default-value nested-k)]
                                                                                   :horizontal-sep?
                                                                                   (pos? (count (children attribute-widget-group)))))
                                             (pack-ancestor-window! attribute-widget-group)
                                             ctx))]))
       (pack! window)
       (add-to-stage! ctx window))
     ctx)))

(declare ->attribute-widget-group)

(defmethod ->value-widget :nested-map [data m ctx]
  (let [attribute-widget-group (->attribute-widget-group ctx m)]
    (actor/set-id! attribute-widget-group :attribute-widget-group)
    (->table ctx {:cell-defaults {:pad 5}
                  :rows (remove nil?
                                [(when (:components data)
                                   [(->add-nested-map-button data attribute-widget-group ctx)])
                                 (when (:components data)
                                   [(->horizontal-separator-cell 1)])
                                 [attribute-widget-group]])})))


(defmethod value-widget->data :nested-map [_ table]
  (attribute-widget-group->data (:attribute-widget-group table)))

;;

(defn- ->play-sound-button [ctx sound-file]
  (->text-button ctx ">>>" #(do (play-sound! % sound-file) %)))

(declare ->sound-columns)

(defn- open-sounds-window! [ctx table]
  (let [rows (for [sound-file (all-sound-files ctx)]
               [(->text-button ctx (str/replace-first sound-file "sounds/" "")
                               (fn [{:keys [context/actor] :as ctx}]
                                 (clear-children! table)
                                 (add-rows! table [(->sound-columns ctx table sound-file)])
                                 (remove! (find-ancestor-window actor))
                                 (pack-ancestor-window! table)
                                 (actor/set-id! table sound-file)
                                 ctx))
                (->play-sound-button ctx sound-file)])]
    (add-to-stage! ctx (->scrollable-choose-window ctx rows)))
  ctx)

(defn- ->sound-columns [ctx table sound-file]
  [(->text-button ctx (name sound-file) #(open-sounds-window! % table))
   (->play-sound-button ctx sound-file)])

(defmethod ->value-widget :sound [_ sound-file ctx]
  (let [table (->table ctx {:cell-defaults {:pad 5}})]
    (add-rows! table [(if sound-file
                        (->sound-columns ctx table sound-file)
                        [(->text-button ctx "No sound" #(open-sounds-window! % table))])])
    table))

;;

(declare ->overview-table)

(defn- add-one-to-many-rows [ctx table property-type properties]
  (let [redo-rows (fn [ctx property-ids]
                    (clear-children! table)
                    (add-one-to-many-rows ctx table property-type (map #(ctx/property ctx %) property-ids))
                    (pack-ancestor-window! table))
        property-ids (set (map :property/id properties))]
    (add-rows! table
               [[(->text-button ctx "+"
                                (fn [ctx]
                                  (let [window (->window ctx {:title "Choose"
                                                              :modal? true
                                                              :close-button? true
                                                              :center? true
                                                              :close-on-escape? true})
                                        clicked-id-fn (fn [ctx id]
                                                        (remove! window)
                                                        (redo-rows ctx (conj property-ids id))
                                                        ctx)]
                                    (add! window (->overview-table ctx property-type clicked-id-fn))
                                    (pack! window)
                                    (add-to-stage! ctx window))
                                  ctx))]
                (for [property properties]
                  (let [image-widget (->image-widget ctx ; image-button/link?
                                                     (core.property/property->image property)
                                                     {:id property})]
                    (add-tooltip! image-widget #(components/info-text property %))
                    image-widget))
                (for [{:keys [property/id]} properties]
                  (->text-button ctx "-" #(do (redo-rows % (disj property-ids id)) %)))])))

(defmethod ->value-widget :one-to-many [data properties context]
  (let [table (->table context {:cell-defaults {:pad 5}})]
    (add-one-to-many-rows context
                          table
                          (:linked-property-type data)
                          properties)
    table))

; TODO use id of the value-widget itself and set/change it
(defmethod value-widget->data :one-to-many [_ widget]
  (->> (children widget)
       (keep actor/id)
       set))

;;

(defn- add-one-to-one-rows [ctx table property-type property]
  (let [redo-rows (fn [ctx id]
                    (clear-children! table)
                    (add-one-to-one-rows ctx table property-type (when id (ctx/property ctx id)))
                    (pack-ancestor-window! table))]
    (add-rows! table
               [[(when-not property
                   (->text-button ctx "+"
                                  (fn [ctx]
                                    (let [window (->window ctx {:title "Choose"
                                                                :modal? true
                                                                :close-button? true
                                                                :center? true
                                                                :close-on-escape? true})
                                          clicked-id-fn (fn [ctx id]
                                                          (remove! window)
                                                          (redo-rows ctx id)
                                                          ctx)]
                                      (add! window (->overview-table ctx property-type clicked-id-fn))
                                      (pack! window)
                                      (add-to-stage! ctx window))
                                    ctx)))]
                [(when property
                   (let [image-widget (->image-widget ctx ; image-button/link?
                                                      (core.property/property->image property)
                                                      {:id property})]
                     (add-tooltip! image-widget #(components/info-text property %))
                     image-widget))]
                [(when property
                   (->text-button ctx "-" #(do
                                            (redo-rows % nil)
                                            %)))]])))

; TODO DRY with one-to-many
(defmethod ->value-widget :one-to-one [data property ctx]
  (let [table (->table ctx {:cell-defaults {:pad 5}})]
    (add-one-to-one-rows ctx
                         table
                         (:linked-property-type data)
                         property)
    table))

(defmethod value-widget->data :one-to-one [_ widget]
  (->> (children widget) (keep actor/id) first))

;;

(defn ->attribute-widget-table [ctx [k v] & {:keys [horizontal-sep?]}]
  (let [label (->label ctx (str k))
        _ (when-let [doc (component/doc k)]
            (add-tooltip! label doc))
        value-widget (->value-widget (component/k->data k) v ctx)
        table (->table ctx {:id k
                            :cell-defaults {:pad 4}})
        column (remove nil?
                       [(when (component/optional? k)
                          (->text-button ctx "-" (fn [ctx]
                                                   (let [window (find-ancestor-window table)]
                                                     (remove! table)
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
  (-> table children last))

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
  (->vertical-group ctx (->attribute-widget-tables ctx props)))

(defn- attribute-widget-group->data [group]
  (into {} (for [k (map actor/id (children group))
                 :let [table (k group)
                       value-widget (attribute-widget-table->value-widget table)]]
             [k (value-widget->data (component/k->data k) value-widget)])))

;;

(defn- apply-context-fn [window f]
  (fn [ctx]
    (try
     (let [ctx (f ctx)]
       (remove! window)
       ctx)
     (catch Throwable t
       (ctx/error-window! ctx t)))))

(defn- ->property-editor-window [context id]
  (let [props (ctx/property context id)
        window (->window context {:title ""
                                  :modal? true
                                  :close-button? true
                                  :center? true
                                  :close-on-escape? true
                                  :cell-defaults {:pad 5}})
        widgets (->attribute-widget-group context props)
        save!   (apply-context-fn window #(core.context/update! % (attribute-widget-group->data widgets)))
        delete! (apply-context-fn window #(core.context/delete! % id))]
    (add-rows! window [[(->scroll-pane-cell context [[{:actor widgets :colspan 2}]
                                                     [(->text-button context "Save [LIGHT_GRAY](ENTER)[]" save!)
                                                      (->text-button context "Delete" delete!)]])]])
    (add-actor! window (->actor context {:act (fn [{:keys [context/state]}]
                                                (when (input/key-just-pressed? input.keys/enter)
                                                  (swap! state save!)))}))
    (pack! window)
    window))

;;

(defn- ->overview-property-widget [{:keys [property/id] :as props} ctx clicked-id-fn extra-info-text scale]
  (let [on-clicked #(clicked-id-fn % id)
        button (if-let [image (core.property/property->image props)]
                 (->image-button ctx image on-clicked {:scale scale})
                 (->text-button ctx (name id) on-clicked))
        top-widget (->label ctx (or (and extra-info-text
                                         (extra-info-text props))
                                    ""))
        stack (->stack ctx [button top-widget])]
    (do
     (add-tooltip! button #(components/info-text props %))
     (set-touchable! top-widget :disabled)
     stack)))

(defn- ->overview-table
  "Creates a table with all-properties of property-type and buttons for each id
  which on-clicked calls clicked-id-fn."
  [ctx property-type clicked-id-fn]
  (let [{:keys [title
                sort-by-fn
                extra-info-text
                columns
                image/scale]} (core.context/overview ctx property-type)
        properties (ctx/all-properties ctx property-type)
        properties (if sort-by-fn
                     (sort-by sort-by-fn properties)
                     properties)]
    (->table ctx
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
    (.left (.add main-table (ctx/->label ctx "[LIGHT_GRAY]Left-Shift: Back to Main Menu[]")))
    (doseq [tab-data tabs-data]
      (.add tabbed-pane (->tab tab-data)))
    main-table))

(defn- open-property-editor-window! [context property-id]
  (add-to-stage! context (->property-editor-window context property-id))
  context)

(defn- ->tabs-data [ctx]
  (for [property-type (ctx/property-types ctx)]
    {:title (:title (core.context/overview ctx property-type))
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
