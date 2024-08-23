(ns screens.property-editor
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [malli.core :as m]
            [gdx.input :as input]
            [gdx.input.keys :as input.keys]
            [core.component :refer [defcomponent] :as component]
            [core.context :as ctx :refer [get-stage ->text-button ->image-button ->label ->text-field ->image-widget ->table ->stack ->window all-sound-files play-sound! ->vertical-group ->check-box ->select-box ->actor add-to-stage! ->scroll-pane get-property all-properties tooltip-text]]
            core.screen
            [core.scene2d.actor :as actor :refer [remove! set-touchable! parent add-listener! add-tooltip! find-ancestor-window pack-ancestor-window!]]
            [core.scene2d.group :refer [add-actor! clear-children! children]]
            [core.scene2d.ui.text-field :as text-field]
            [core.scene2d.ui.table :refer [add! add-rows! cells ->horizontal-separator-cell ->vertical-separator-cell]]
            [core.scene2d.ui.cell :refer [set-actor!]]
            [core.scene2d.ui.widget-group :refer [pack!]]
            [widgets.error-modal :refer [error-window!]]))

(defn- ->scroll-pane-cell [ctx rows]
  (let [table (->table ctx {:rows rows
                            :cell-defaults {:pad 1}
                            :pack? true})
        scroll-pane (->scroll-pane ctx table)]
    {:actor scroll-pane
     :width (+ (actor/width table) 200)
     :height (min (- (ctx/gui-viewport-height ctx) 50) (actor/height table))}))

(defn ->scrollable-choose-window [ctx rows]
  (->window ctx {:title "Choose"
                 :modal? true
                 :close-button? true
                 :center? true
                 :close-on-escape? true
                 :rows [[(->scroll-pane-cell ctx rows)]]
                 :pack? true}))

;;

; TODO save button show if changes made, otherwise disabled?
; when closing (lose changes? yes no)

; TODO overview table not refreshed after changes in property editor window

;;

(defn- attr->value-widget [k]
  (or (:widget (get component/attributes k)) :label))

(defmulti ->value-widget     (fn [[k _v] _ctx] (attr->value-widget k)))
(defmulti value-widget->data (fn [k _widget]   (attr->value-widget k)))

(defmethod value-widget->data :default [_ widget]
  (actor/id widget))

;;

(defn ->edn [v]
  (binding [*print-level* nil]
    (pr-str v)))

(defmethod ->value-widget :label [[_ v] ctx]
  (->label ctx (->edn v)))

;;

(defmethod ->value-widget :text-field [[k v] ctx]
  (let [widget (->text-field ctx (->edn v) {})]
    (add-tooltip! widget (str "Schema: " (pr-str (m/form (:schema (get component/attributes k))))))
    widget))

(defmethod value-widget->data :text-field [_ widget]
  (edn/read-string (text-field/text widget)))

;;

(defmethod ->value-widget :check-box [[k checked?] ctx]
  (assert (boolean? checked?))
  (->check-box ctx "" (fn [_]) checked?))

(defmethod value-widget->data :check-box [_ widget]
  (.isChecked ^com.kotcrab.vis.ui.widget.VisCheckBox widget))

;;

(defmethod ->value-widget :enum [[k v] ctx]
  (->select-box ctx {:items (map ->edn (:items (component/attributes k)))
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

(defmethod ->value-widget :image [[_ image] ctx]
  (->image-widget ctx image {})
  #_(->image-button ctx image
                  #(do (add-to-stage! % (->scrollable-choose-window % (texture-rows %))) %)
                  {:dimensions [96 96]})) ; x2  , not hardcoded here TODO

;;

; looping? - click on widget restart
; frame-duration
; frames ....
; hidden actor act tick atom animation & set current frame image drawable
(defmethod ->value-widget :animation [[_ animation] ctx]
  (->table ctx {:rows [(for [image (:frames animation)]
                         (->image-widget ctx image {}))]
                :cell-defaults {:pad 1}}))

;;

(declare ->property-editor-window)

(defn open-property-editor-window! [context property-id]
  (add-to-stage! context (->property-editor-window context property-id))
  context)

(defmethod ->value-widget :link-button [[_ prop-id] context]
  (->text-button context (name prop-id) #(open-property-editor-window! % prop-id)))

;;

(declare ->attribute-widget-table
         attribute-widget-group->data)

(defn- ->add-nested-map-button [ctx k attribute-widget-group]
  (->text-button ctx (str "Add " (name k) " component")
   (fn [ctx]
     (let [window (->window ctx {:title "Choose"
                                 :modal? true
                                 :close-button? true
                                 :center? true
                                 :close-on-escape? true
                                 :cell-defaults {:pad 5}})]
       (add-rows! window (for [nested-k (sort (remove (set (keys (attribute-widget-group->data attribute-widget-group)))
                                                      (:components (component/attributes k))))]
                           [(->text-button ctx (name nested-k)
                                           (fn [ctx]
                                             (remove! window)
                                             (add-actor! attribute-widget-group
                                                         (->attribute-widget-table ctx
                                                                                   [nested-k (:default-value (component/attributes nested-k))]
                                                                                   :horizontal-sep?
                                                                                   (pos? (count (children attribute-widget-group)))))
                                             (pack-ancestor-window! attribute-widget-group)
                                             ctx))]))
       (pack! window)
       (add-to-stage! ctx window))
     ctx)))

(declare ->attribute-widget-group)

(defmethod ->value-widget :nested-map [[k props] ctx]
  (let [attribute-widget-group (->attribute-widget-group ctx props)]
    (actor/set-id! attribute-widget-group :attribute-widget-group)
    (->table ctx {:cell-defaults {:pad 5}
                  :rows (remove nil?
                                [(when (:components (component/attributes k))
                                   [(->add-nested-map-button ctx k attribute-widget-group)])
                                 (when (:components (component/attributes k))
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

(defmethod ->value-widget :sound [[_ sound-file] ctx]
  (let [table (->table ctx {:cell-defaults {:pad 5}})]
    (add-rows! table [(if sound-file
                        (->sound-columns ctx table sound-file)
                        [(->text-button ctx "No sound" #(open-sounds-window! % table))])])
    table))

;;

(declare ->overview-table)

(defn- add-one-to-many-rows [ctx table property-type property-ids]
  (let [redo-rows (fn [ctx property-ids]
                    (clear-children! table)
                    (add-one-to-many-rows ctx table property-type property-ids)
                    (pack-ancestor-window! table))]
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
                                                        (redo-rows ctx (conj (set property-ids) id))
                                                        ctx)]
                                    (add! window (->overview-table ctx property-type clicked-id-fn))
                                    (pack! window)
                                    (add-to-stage! ctx window))
                                  ctx))]
                (for [prop-id property-ids]
                  (let [props (get-property ctx prop-id)
                        ; TODO also x2 dimensions
                        image-widget (->image-widget ctx ; TODO image-button (link)
                                                     (:property/image props)
                                                     {:id (:property/id props)})]
                    (add-tooltip! image-widget #(tooltip-text % props))
                    image-widget))
                (for [prop-id property-ids]
                  (->text-button ctx "-"
                                 #(do (redo-rows % (disj (set property-ids) prop-id)) %)))])))

(defmethod ->value-widget :one-to-many [[attribute property-ids] context]
  (let [table (->table context {:cell-defaults {:pad 5}})]
    (add-one-to-many-rows context
                          table
                          (:linked-property-type (component/attributes attribute))
                          property-ids)
    table))

; TODO use id of the value-widget itself and set/change it
(defmethod value-widget->data :one-to-many [_ widget]
  (->> (children widget) (keep actor/id) set))

;;

; define for each property-type as exactly the order for property-editor-window
; :property-editor-order
; or make always alphabetically?
(defn- sort-attributes [properties]
  (sort-by
   (fn [[k _v]]
     [(case k
        :property/id 0
        :property/image 1
        :property/pretty-name 2
        :property/bounds 3

        :entity/animation 2
        :item/slot 3
        :entity/faction 5
        :entity/flying? 6
        :entity/reaction-time 8

        :creature/level 3
        :creature/species 4

        :skill/start-action-sound 3
        :skill/action-time-modifier-key 4
        11)
      (name k)])
   properties))

; TODO this is == :optional key @ components-attribute ?
(defn- removable-component? [k]
  ;(#{"tx" "modifier" "stats" #_"entity"} (namespace k))
  true

  )

(defn ->attribute-widget-table [ctx [k v] & {:keys [horizontal-sep?]}]
  (let [label (->label ctx (name k))
        _ (when-let [doc (:doc (get component/attributes k))]
            (add-tooltip! label doc))
        value-widget (->value-widget [k v] ctx)
        table (->table ctx {:id k
                            :cell-defaults {:pad 4}})
        column (remove nil?
                       [(when (removable-component? k)
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
             [k (value-widget->data k value-widget)])))

;;

(defn- apply-context-fn [window f]
  (fn [ctx]
    (try
     (remove! window)
     (f ctx)
     (catch Throwable t
       (error-window! ctx t)
       ctx))))

(defn ->property-editor-window [context id]
  (let [props (get-property context id)
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
                                                     [(->text-button context "Save" save!)
                                                      (->text-button context "Delete" delete!)]])]])
    (add-actor! window (->actor context {:act (fn [{:keys [context/state]}]
                                                (when (input/key-just-pressed? input.keys/enter)
                                                  (swap! state save!)))}))
    (pack! window)
    window))

;;

(defn- ->overview-table
  "Creates a table with all-properties of property-type and buttons for each id
  which on-clicked calls clicked-id-fn."
  [ctx property-type clicked-id-fn]
  (let [{:keys [title
                sort-by-fn
                extra-info-text
                columns
                image/dimensions]} (core.context/overview ctx property-type)
        entities (all-properties ctx property-type)
        entities (if sort-by-fn
                   (sort-by sort-by-fn entities)
                   entities)
        number-columns columns]
    (->table ctx
             {:cell-defaults {:pad 2}
              :rows (concat [[{:actor (->label ctx title) :colspan number-columns}]]
                            (for [entities (partition-all number-columns entities)] ; TODO can just do 1 for?
                              (for [{:keys [property/id] :as props} entities
                                    :let [on-clicked #(clicked-id-fn % id)
                                          button (if (:property/image props)
                                                   (->image-button ctx (:property/image props) on-clicked
                                                                   {:dimensions dimensions})
                                                   (->text-button ctx (name id) on-clicked))
                                          top-widget (->label ctx (or (and extra-info-text
                                                                           (extra-info-text props))
                                                                      ""))
                                          stack (->stack ctx [button top-widget])]]
                                (do
                                 (add-tooltip! button #(tooltip-text % props))
                                 (set-touchable! top-widget :disabled)
                                 stack))))})))

(defn- set-second-widget! [context widget]
  (let [table (:main-table (get-stage context))]
    (set-actor! (second (cells table)) widget)
    (pack! table)))

(defn- ->left-widget [context]
  (->table context {:cell-defaults {:pad 5}
                    :rows (concat
                           (for [property-type (ctx/property-types context)]
                             [(->text-button context
                                             (:title (core.context/overview context property-type))
                                             #(do (set-second-widget! % (->overview-table % property-type open-property-editor-window!))
                                                  %))])
                           [[(->text-button context "Back to Main Menu" #(ctx/change-screen % :screens/main-menu))]])}))

(defcomponent :screens/property-editor {}
  (component/create [_ ctx]
    (ctx/->stage-screen ctx
                        {:actors [(ctx/->background-image ctx)
                                  (->table ctx {:id :main-table
                                                :rows [[(->left-widget ctx) nil]]
                                                :fill-parent? true})]})))
