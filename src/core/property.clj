; all-properties
; prop-image
; validate-and-create
; :data/animation ... ?
(ns core.property
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [clojure.edn :as edn]
            clojure.pprint
            [malli.core :as m]
            [malli.error :as me]
            [malli.generator :as mg]
            [core.ctx :refer :all]
            [core.ui :as ui])
  (:import com.badlogic.gdx.Input$Keys
           (com.kotcrab.vis.ui.widget VisCheckBox VisSelectBox VisTextField)))

(defsystem ->value "..." [_])

(defn- data-component [k]
  (try (let [data (:data (safe-get component-attributes k))]
         (if (vector? data)
           [(first data) (->value data)]
           [data (safe-get component-attributes data)]))
       (catch Throwable t
         (throw (ex-info "" {:k k} t)))))

(defmulti edn->value (fn [data v ctx] (if data (data 0))))
(defmethod edn->value :default [_data v _ctx]
  v)

(defn- k->widget [k]
  (cond
   (#{:map-optional :components-ns} k) :map
   (#{:number :nat-int :int :pos :pos-int :val-max} k) :number
   :else k))

(defmulti ->widget      (fn [[k _] _v _ctx] (k->widget k)))
(defmulti widget->value (fn [[k _] _widget] (k->widget k)))

(defn- property-type->id-namespace [property-type]
  (keyword (name property-type)))

(defn- ns-k->property-type [ns-k]
  (keyword "properties" (name ns-k)))

(defn- ->type [{:keys [property/id]}]
  (keyword "properties" (namespace id)))

(defn ->image [{:keys [entity/image entity/animation]}] ; FIXME replaces ctx var
  (or image
      (first (:frames animation))))

(defn- types []
  (filter #(= "properties" (namespace %)) (keys component-attributes)))

(defn- overview [property-type]
  (:overview (get component-attributes property-type)))

(defn ->schema [property]
  (-> property
      ->type
      data-component
      (get 1)
      :schema
      m/schema))

(defn- validate [property]
  (let [schema (->schema property)
        valid? (try (m/validate schema property)
                    (catch Throwable t
                      (throw (ex-info "m/validate fail" {:property property} t))))]
    (when-not valid?
      (throw (ex-info (str (me/humanize (m/explain schema property)))
                      {:property property
                       :schema (m/form schema)})))))

(defn validate-and-create [file]
  (let [properties (-> file slurp edn/read-string)]
    (assert (apply distinct? (map :property/id properties)))
    (run! validate properties)
    {:file file
     :db (zipmap (map :property/id properties) properties)}))

(defn- async-pprint-spit! [ctx file data]
  (.start
   (Thread.
    (fn []
      (binding [*print-level* nil]
        (->> data
             clojure.pprint/pprint
             with-out-str
             (spit file)))))))

(defn- recur-sort-map [m]
  (into (sorted-map)
        (zipmap (keys m)
                (map #(if (map? %)
                        (recur-sort-map %)
                        %)
                     (vals m)))))

(defn- async-write-to-file! [{{:keys [db file]} :context/properties :as ctx}]
  (->> db
       vals
       (sort-by ->type)
       (map recur-sort-map)
       doall
       (async-pprint-spit! ctx file))
  ctx)

(def ^:private undefined-data-ks (atom #{}))

(comment
 #{:frames
   :looping?
   :frame-duration
   :file
   :sub-image-bounds})

(defn- apply-kvs
  "Calls for every key in map (f k v) to calculate new value at k."
  [m f]
  (reduce (fn [m k]
            (assoc m k (f k (get m k)))) ; using assoc because non-destructive for records
          m
          (keys m)))

(defn- build [ctx property]
  (apply-kvs property
             (fn [k v]
               (edn->value (try (data-component k)
                                (catch Throwable _t
                                  (swap! undefined-data-ks conj k)))
                           (if (map? v) (build ctx v) v)
                           ctx))))

(extend-type core.ctx.Context
  Property
  (build-property [{{:keys [db]} :context/properties :as ctx} id]
    (build ctx (safe-get db id))))

(defn all-properties [{{:keys [db]} :context/properties :as ctx} type]
  (->> (vals db)
       (filter #(= type (->type %)))
       (map #(build ctx %))))

(defn update! [{{:keys [db]} :context/properties :as ctx} {:keys [property/id] :as property}]
  {:pre [(contains? property :property/id)
         (contains? db id)]}
  (validate property)
  (-> ctx
      (update-in [:context/properties :db] assoc id property)
      async-write-to-file!))

(defn delete! [{{:keys [db]} :context/properties :as ctx} property-id]
  {:pre [(contains? db property-id)]}
  (-> ctx
      (update-in [:context/properties :db] dissoc property-id)
      async-write-to-file!))

(comment
 (defn- migrate [property-type prop-fn]
   (let [ctx @app/state]
     (time
      ; TODO work directly on edn, no all-properties, use :db
      (doseq [prop (map prop-fn (all-properties ctx property-type))]
        (println (:property/id prop) ", " (:property/pretty-name prop))
        (swap! app/state update! prop)))
     (async-write-to-file! @app/state)
     nil))

 (migrate :properties/creature
          (fn [prop]
            (-> prop
                (dissoc :entity/reaction-time)
                (update :property/stats assoc :stats/reaction-time
                        (max (int (/ (:entity/reaction-time prop) 0.016))
                             2)))))
 )

(defcomponent :some {:schema :some})
(defcomponent :boolean {:schema :boolean})

(defmethod ->widget :boolean [_ checked? _ctx]
  (assert (boolean? checked?))
  (ui/->check-box "" (fn [_]) checked?))

(defmethod widget->value :boolean [_ widget]
  (.isChecked ^VisCheckBox widget))

(defcomponent :string {:schema :string})

(defn- add-schema-tooltip! [widget data]
  (ui/add-tooltip! widget (str "Schema: " (pr-str (m/form (:schema data)))))
  widget)

(defmethod ->widget :string [[_ data] v _ctx]
  (add-schema-tooltip! (ui/->text-field v {})
                       data))

(defmethod widget->value :string [_ widget]
  (.getText ^VisTextField widget))

(defcomponent :number  {:schema number?})
(defcomponent :nat-int {:schema nat-int?})
(defcomponent :int     {:schema int?})
(defcomponent :pos     {:schema pos?})
(defcomponent :pos-int {:schema pos-int?})

(defn- ->edn-str [v]
  (binding [*print-level* nil]
    (pr-str v)))


(defmethod ->widget :number [[_ data] v _ctx]
  (add-schema-tooltip! (ui/->text-field (->edn-str v) {})
                       data))

(defmethod widget->value :number [_ widget]
  (edn/read-string (.getText ^VisTextField widget)))

(defcomponent :enum
  (->value [[_ items]]
    {:schema (apply vector :enum items)}))

(defmethod ->widget :enum [[_ data] v _ctx]
  (ui/->select-box {:items (map ->edn-str (rest (:schema data)))
                    :selected (->edn-str v)}))

(defmethod widget->value :enum [_ widget]
  (edn/read-string (.getSelected ^VisSelectBox widget)))

(defcomponent :qualified-keyword
  (->value [schema]
    {:schema schema}))

(defn- attribute-schema
  "Can define keys as just keywords or with properties like [:foo {:optional true}]."
  [ks]
  (for [k ks
        :let [k? (keyword? k)
              properties (if k? nil (k 1))
              k (if k? k (k 0))]]
    (do
     (assert (keyword? k))
     (assert (or (nil? properties) (map? properties)) (pr-str ks))
     [k properties (:schema ((data-component k) 1))])))

(defn- map-schema [ks]
  (apply vector :map {:closed true} (attribute-schema ks)))

(defcomponent :map
  (->value [[_ ks]]
    {:schema (map-schema ks)}))

(defcomponent :map-optional
  (->value [[_ ks]]
    {:schema (map-schema (map (fn [k] [k {:optional true}]) ks))}))

(defn- namespaced-ks [ns-name-k]
  (filter #(= (name ns-name-k) (namespace %))
          (keys component-attributes)))

(defcomponent :components-ns
  (->value [[_ ns-name-k]]
    (->value [:map-optional (namespaced-ks ns-name-k)])))

(defcomponent :image
  {:schema [:map {:closed true}
            [:file :string]
            [:sub-image-bounds {:optional true} [:vector {:size 4} nat-int?]]]})

(defmethod edn->value :image [_ image ctx]
  (edn->image image ctx))

; too many ! too big ! scroll ... only show files first & preview?
; make tree view from folders, etc. .. !! all creatures animations showing...
(defn- texture-rows [ctx]
  (for [file (sort (:texture-files (assets ctx)))]
    [(ui/->image-button (->image ctx file) identity)]
    #_[(ui/->text-button file identity)]))

(defmethod ->widget :image [_ image ctx]
  (ui/->image-widget (edn->image image ctx) {})
  #_(ui/->image-button image
                       #(ui/stage-add! % (->scrollable-choose-window % (texture-rows %)))
                       {:dimensions [96 96]})) ; x2  , not hardcoded here

(defcomponent :sound {:schema :string})

(defn- ->scrollable-choose-window [ctx rows]
  (ui/->window {:title "Choose"
                :modal? true
                :close-button? true
                :center? true
                :close-on-escape? true
                :rows [[(ui/->scroll-pane-cell ctx rows)]]
                :pack? true}))

(defn- ->play-sound-button [sound-file]
  (ui/->text-button "play!" #(play-sound! % sound-file)))

(declare ->sound-columns)

(defn- open-sounds-window! [ctx table]
  (let [rows (for [sound-file (:sound-files (assets ctx))]
               [(ui/->text-button (str/replace-first sound-file "sounds/" "")
                                  (fn [{:keys [context/actor] :as ctx}]
                                    (ui/clear-children! table)
                                    (ui/add-rows! table [(->sound-columns table sound-file)])
                                    (ui/remove! (ui/find-ancestor-window actor))
                                    (ui/pack-ancestor-window! table)
                                    (ui/set-id! table sound-file)
                                    ctx))
                (->play-sound-button sound-file)])]
    (ui/stage-add! ctx (->scrollable-choose-window ctx rows))))

(defn- ->sound-columns [table sound-file]
  [(ui/->text-button (name sound-file) #(open-sounds-window! % table))
   (->play-sound-button sound-file)])

(defmethod ->widget :sound [_ sound-file _ctx]
  (let [table (ui/->table {:cell-defaults {:pad 5}})]
    (ui/add-rows! table [(if sound-file
                           (->sound-columns table sound-file)
                           [(ui/->text-button "No sound" #(open-sounds-window! % table))])])
    table))

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

(defmethod ->widget :default [_ v _ctx]
  (ui/->label (truncate (->edn-str v) 60)))

(defmethod widget->value :default [_ widget]
  (ui/actor-id widget))

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
  (let [[data-type {:keys [schema]}] (data-component k)]
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
                                                  (ui/remove! window)
                                                  (ui/add-actor! attribute-widget-group
                                                                 (->component-widget ctx
                                                                                     [k (get k-props k) (k->default-value k)]
                                                                                     :horizontal-sep?
                                                                                     (pos? (count (ui/children attribute-widget-group)))))
                                                  (ui/pack-ancestor-window! attribute-widget-group)
                                                  ctx))]))
      (.pack window)
      (ui/stage-add! ctx window))))

(declare ->attribute-widget-group)

(defn- optional-keyset [schema]
  (set (map first
            (filter (fn [[k prop-m]] (:optional prop-m))
                    (k-properties schema)))))

(defmethod ->widget :map [[_ data] m ctx]
  (let [attribute-widget-group (->attribute-widget-group ctx (:schema data) m)
        optional-keys-left? (seq (set/difference (optional-keyset (:schema data))
                                                 (set (keys m))))]
    (ui/set-id! attribute-widget-group :attribute-widget-group)
    (ui/->table {:cell-defaults {:pad 5}
                 :rows (remove nil?
                               [(when optional-keys-left?
                                  [(ui/->text-button "Add component"
                                                     (->choose-component-window data attribute-widget-group))])
                                (when optional-keys-left?
                                  [(ui/->horizontal-separator-cell 1)])
                                [attribute-widget-group]])})))


(defmethod widget->value :map [_ table]
  (attribute-widget-group->data (:attribute-widget-group table)))

(defn- ->attribute-label [k]
  (let [label (ui/->label (str k))]
    (when-let [doc (:editor/doc (get component-attributes k))]
      (ui/add-tooltip! label doc))
    label))

(defn- ->component-widget [ctx [k k-props v] & {:keys [horizontal-sep?]}]
  (let [label (->attribute-label k)
        value-widget (->widget (data-component k) v ctx)
        table (ui/->table {:id k
                           :cell-defaults {:pad 4}})
        column (remove nil?
                       [(when (:optional k-props)
                          (ui/->text-button "-" (fn [ctx]
                                                  (let [window (ui/find-ancestor-window table)]
                                                    (ui/remove! table)
                                                    (.pack window))
                                                  ctx)))
                        label
                        (ui/->vertical-separator-cell)
                        value-widget])
        rows [(when horizontal-sep? [(ui/->horizontal-separator-cell (count column))])
              column]]
    (ui/set-id! value-widget v)
    (ui/add-rows! table (remove nil? rows))
    table))

(defn- attribute-widget-table->value-widget [table]
  (-> table ui/children last))

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
  (into {} (for [k (map ui/actor-id (ui/children group))
                 :let [table (k group)
                       value-widget (attribute-widget-table->value-widget table)]]
             [k (widget->value (data-component k) value-widget)])))

;;

(defn- apply-context-fn [window f]
  (fn [ctx]
    (try
     (let [ctx (f ctx)]
       (ui/remove! window)
       ctx)
     (catch Throwable t
       (ui/error-window! ctx t)))))

(defn- ->property-editor-window [ctx id]
  (let [props (safe-get (:db (:context/properties ctx)) id)
        window (ui/->window {:title "Edit Property"
                             :modal? true
                             :close-button? true
                             :center? true
                             :close-on-escape? true
                             :cell-defaults {:pad 5}})
        widgets (->attribute-widget-group ctx (->schema props) props)
        save!   (apply-context-fn window #(update! % (attribute-widget-group->data widgets)))
        delete! (apply-context-fn window #(delete! % id))]
    (ui/add-rows! window [[(ui/->scroll-pane-cell ctx [[{:actor widgets :colspan 2}]
                                                       [(ui/->text-button "Save [LIGHT_GRAY](ENTER)[]" save!)
                                                        (ui/->text-button "Delete" delete!)]])]])
    (ui/add-actor! window
                      (ui/->actor {:act (fn [_ctx]
                                          (when (.isKeyJustPressed gdx-input Input$Keys/ENTER)
                                            (swap! app-state save!)))}))
    (.pack window)
    window))

(defn- ->overview-property-widget [{:keys [property/id] :as props} clicked-id-fn extra-info-text scale]
  (let [on-clicked #(clicked-id-fn % id)
        button (if-let [image (->image props)]
                 (ui/->image-button image on-clicked {:scale scale})
                 (ui/->text-button (name id) on-clicked))
        top-widget (ui/->label (or (and extra-info-text (extra-info-text props)) ""))
        stack (ui/->stack [button top-widget])]
    (ui/add-tooltip! button #(->info-text props %))
    (ui/set-touchable! top-widget :disabled)
    stack))

(defn- ->overview-table [ctx property-type clicked-id-fn]
  (let [{:keys [sort-by-fn
                extra-info-text
                columns
                image/scale]} (overview property-type)
        properties (all-properties ctx property-type)
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
  (ui/stage-add! context (->property-editor-window context property-id)))

(defn- ->tabs-data [ctx]
  (for [property-type (sort (types))]
    {:title (:title (overview property-type))
     :content (->overview-table ctx property-type open-property-editor-window!)}))

(import 'com.badlogic.gdx.scenes.scene2d.InputListener)

(derive :screens/property-editor :screens/stage)
(defcomponent :screens/property-editor
  (->mk [_ ctx]
    {:stage (let [stage (ui/->stage ctx [(ui/->background-image ctx)
                                         (->tabbed-pane (->tabs-data ctx))])]
              (.addListener stage (proxy [InputListener] []
                                    (keyDown [event keycode]
                                      (if (= keycode Input$Keys/SHIFT_LEFT)
                                        (do
                                         (swap! app-state change-screen :screens/main-menu)
                                         true)
                                        false))))
              stage)}))

; TODO schemas not checking if that property exists in db...
; https://github.com/damn/core/issues/59

(defcomponent :one-to-many
  (->value [[_ property-type]]
    {:schema [:set [:qualified-keyword {:namespace (property-type->id-namespace property-type)}]]}))

(defn- one-to-many-schema->linked-property-type [[_set [_qualif_kw {:keys [namespace]}]]]
  (ns-k->property-type namespace))

(comment
 (= (one-to-many-schema->linked-property-type [:set [:qualified-keyword {:namespace :items}]])
    :properties/items)
 )

(defmethod edn->value :one-to-many [_ property-ids ctx]
  (map (partial build-property ctx) property-ids))

(defcomponent :one-to-one
  (->value [[_ property-type]]
    {:schema [:qualified-keyword {:namespace (property-type->id-namespace property-type)}]}))

(defn- one-to-one-schema->linked-property-type [[_qualif_kw {:keys [namespace]}]]
  (ns-k->property-type namespace))

(comment
 (= (one-to-one-schema->linked-property-type [:qualified-keyword {:namespace :creatures}])
    :properties/creatuers)
 )

(defmethod edn->value :one-to-one [_ property-id ctx]
  (build-property ctx property-id))

(defn- add-one-to-many-rows [ctx table property-type property-ids]
  (let [redo-rows (fn [ctx property-ids]
                    (ui/clear-children! table)
                    (add-one-to-many-rows ctx table property-type property-ids)
                    (ui/pack-ancestor-window! table))]
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
                                                 (ui/remove! window)
                                                 (redo-rows ctx (conj property-ids id))
                                                 ctx)]
                             (.add window (->overview-table ctx property-type clicked-id-fn))
                             (.pack window)
                             (ui/stage-add! ctx window))))]
      (for [property-id property-ids]
        (let [property (build-property ctx property-id)
              image-widget (ui/->image-widget (->image property)
                                              {:id property-id})]
          (ui/add-tooltip! image-widget #(->info-text property %))
          image-widget))
      (for [id property-ids]
        (ui/->text-button "-" #(do (redo-rows % (disj property-ids id)) %)))])))

(defmethod ->widget :one-to-many [[_ data] property-ids context]
  (let [table (ui/->table {:cell-defaults {:pad 5}})]
    (add-one-to-many-rows context
                          table
                          (one-to-many-schema->linked-property-type (:schema data))
                          property-ids)
    table))

(defmethod widget->value :one-to-many [_ widget]
  (->> (ui/children widget)
       (keep ui/actor-id)
       set))

(defn- add-one-to-one-rows [ctx table property-type property-id]
  (let [redo-rows (fn [ctx id]
                    (ui/clear-children! table)
                    (add-one-to-one-rows ctx table property-type id)
                    (ui/pack-ancestor-window! table))]
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
                                                   (ui/remove! window)
                                                   (redo-rows ctx id)
                                                   ctx)]
                               (.add window (->overview-table ctx property-type clicked-id-fn))
                               (.pack window)
                               (ui/stage-add! ctx window)))))]
      [(when property-id
         (let [property (build-property ctx property-id)
               image-widget (ui/->image-widget (->image property)
                                               {:id property-id})]
           (ui/add-tooltip! image-widget #(->info-text property %))
           image-widget))]
      [(when property-id
         (ui/->text-button "-" #(do (redo-rows % nil) %)))]])))

(defmethod ->widget :one-to-one [[_ data] property-id ctx]
  (let [table (ui/->table {:cell-defaults {:pad 5}})]
    (add-one-to-one-rows ctx
                         table
                         (one-to-one-schema->linked-property-type (:schema data))
                         property-id)
    table))

(defmethod widget->value :one-to-one [_ widget]
  (->> (ui/children widget) (keep ui/actor-id) first))

(defcomponent :val-max {:schema (m/form val-max-schema)})
