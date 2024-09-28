(in-ns 'clojure.ctx)

(defsystem ->value "..." [_])

(defn def-attributes [& attributes-data]
  {:pre [(even? (count attributes-data))]}
  (doseq [[k data] (partition 2 attributes-data)]
    (defcomponent* k {:data data})))

(defn def-type [k {:keys [schema overview]}]
  (defcomponent k
    {:data [:map (conj schema :property/id)]
     :overview overview}))

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

(defn prop->image [{:keys [entity/image entity/animation]}]
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

(defcomponent :property/id {:data [:qualified-keyword]})

(defn- ->ctx-properties
  "Validates all properties."
  [properties-edn-file]
  (let [properties (-> properties-edn-file slurp edn/read-string)]
    (assert (apply distinct? (map :property/id properties)))
    (run! validate properties)
    {:context/properties {:file properties-edn-file
                          :db (zipmap (map :property/id properties) properties)}}))

(defn- async-pprint-spit! [ctx file data]
  (.start
   (Thread.
    (fn []
      (binding [*print-level* nil]
        (->> data
             pprint
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

(defn build-property [{{:keys [db]} :context/properties :as ctx} id]
  (build ctx (safe-get db id)))

(defn all-properties [{{:keys [db]} :context/properties :as ctx} type]
  (->> (vals db)
       (filter #(= type (->type %)))
       (map #(build ctx %))))

(defn- update! [{{:keys [db]} :context/properties :as ctx} {:keys [property/id] :as property}]
  {:pre [(contains? property :property/id)
         (contains? db id)]}
  (validate property)
  (-> ctx
      (update-in [:context/properties :db] assoc id property)
      async-write-to-file!))

(defn- delete! [{{:keys [db]} :context/properties :as ctx} property-id]
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

;;;;

(defn- add-schema-tooltip! [widget data]
  (add-tooltip! widget (str "Schema: " (pr-str (m/form (:schema data)))))
  widget)

(defn- ->edn-str [v]
  (binding [*print-level* nil]
    (pr-str v)))

(defmethod ->widget :boolean [_ checked? _ctx]
  (assert (boolean? checked?))
  (->check-box "" (fn [_]) checked?))

(defmethod widget->value :boolean [_ widget]
  (.isChecked ^com.kotcrab.vis.ui.widget.VisCheckBox widget))

(defmethod ->widget :string [[_ data] v _ctx]
  (add-schema-tooltip! (->text-field v {})
                       data))

(defmethod widget->value :string [_ widget]
  (.getText ^com.kotcrab.vis.ui.widget.VisTextField widget))

(defmethod ->widget :number [[_ data] v _ctx]
  (add-schema-tooltip! (->text-field (->edn-str v) {})
                       data))

(defmethod widget->value :number [_ widget]
  (edn/read-string (.getText ^com.kotcrab.vis.ui.widget.VisTextField widget)))

(defmethod ->widget :enum [[_ data] v _ctx]
  (->select-box {:items (map ->edn-str (rest (:schema data)))
                    :selected (->edn-str v)}))

(defmethod widget->value :enum [_ widget]
  (edn/read-string (.getSelected ^com.kotcrab.vis.ui.widget.VisSelectBox widget)))

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

(defn- namespaced-ks [ns-name-k]
  (filter #(= (name ns-name-k) (namespace %))
          (keys component-attributes)))

;;;; Component Data Schemas

(defcomponent :some    {:schema :some})
(defcomponent :boolean {:schema :boolean})
(defcomponent :string  {:schema :string})
(defcomponent :number  {:schema number?})
(defcomponent :nat-int {:schema nat-int?})
(defcomponent :int     {:schema int?})
(defcomponent :pos     {:schema pos?})
(defcomponent :pos-int {:schema pos-int?})
(defcomponent :sound   {:schema :string})
(defcomponent :val-max {:schema (m/form val-max-schema)})
(defcomponent :image   {:schema [:map {:closed true}
                                 [:file :string]
                                 [:sub-image-bounds {:optional true} [:vector {:size 4} nat-int?]]]})
(defcomponent :data/animation {:schema [:map {:closed true}
                                        [:frames :some]
                                        [:frame-duration pos?]
                                        [:looping? :boolean]]})

(defcomponent :enum
  (->value [[_ items]]
    {:schema (apply vector :enum items)}))

(defcomponent :qualified-keyword
  (->value [schema]
    {:schema schema}))

(defcomponent :map
  (->value [[_ ks]]
    {:schema (map-schema ks)}))

(defcomponent :map-optional
  (->value [[_ ks]]
    {:schema (map-schema (map (fn [k] [k {:optional true}]) ks))}))

(defcomponent :components-ns
  (->value [[_ ns-name-k]]
    (->value [:map-optional (namespaced-ks ns-name-k)])))

(defcomponent :one-to-many
  (->value [[_ property-type]]
    {:schema [:set [:qualified-keyword {:namespace (property-type->id-namespace property-type)}]]}))

(defcomponent :one-to-one
  (->value [[_ property-type]]
    {:schema [:qualified-keyword {:namespace (property-type->id-namespace property-type)}]}))

;;;;

(defmethod edn->value :image [_ image ctx]
  (edn->image image ctx))

; too many ! too big ! scroll ... only show files first & preview?
; make tree view from folders, etc. .. !! all creatures animations showing...
(defn- texture-rows [ctx]
  (for [file (sort (:texture-files (ctx-assets ctx)))]
    [(->image-button (prop->image ctx file) identity)]
    #_[(->text-button file identity)]))

(defmethod ->widget :image [_ image ctx]
  (->image-widget (edn->image image ctx) {})
  #_(->image-button image
                       #(stage-add! % (->scrollable-choose-window % (texture-rows %)))
                       {:dimensions [96 96]})) ; x2  , not hardcoded here


(defn- ->scrollable-choose-window [ctx rows]
  (->window {:title "Choose"
             :modal? true
             :close-button? true
             :center? true
             :close-on-escape? true
             :rows [[(->scroll-pane-cell ctx rows)]]
             :pack? true}))

(defn- ->play-sound-button [sound-file]
  (->text-button "play!" #(play-sound! % sound-file)))

(declare ->sound-columns)

(defn- open-sounds-window! [ctx table]
  (let [rows (for [sound-file (:sound-files (ctx-assets ctx))]
               [(->text-button (str/replace-first sound-file "sounds/" "")
                                  (fn [{:keys [context/actor] :as ctx}]
                                    (clear-children! table)
                                    (add-rows! table [(->sound-columns table sound-file)])
                                    (remove! (find-ancestor-window actor))
                                    (pack-ancestor-window! table)
                                    (set-id! table sound-file)
                                    ctx))
                (->play-sound-button sound-file)])]
    (stage-add! ctx (->scrollable-choose-window ctx rows))))

(defn- ->sound-columns [table sound-file]
  [(->text-button (name sound-file) #(open-sounds-window! % table))
   (->play-sound-button sound-file)])

(defmethod ->widget :sound [_ sound-file _ctx]
  (let [table (->table {:cell-defaults {:pad 5}})]
    (add-rows! table [(if sound-file
                        (->sound-columns table sound-file)
                        [(->text-button "No sound" #(open-sounds-window! % table))])])
    table))

; TODO main properties optional keys to add them itself not possible (e.g. to add skill/cooldown back)
; TODO save button show if changes made, otherwise disabled?
; when closing (lose changes? yes no)
; TODO overview table not refreshed after changes in property editor window
; * don't show button if no components to add anymore (use remaining-ks)
; * what is missing to remove the button once the last optional key was added (not so important)
; maybe check java property/game/db/editors .... unity? rpgmaker? gamemaker?

(declare property-k-sort-order)

(defn- component-order [[k _v]]
  (or (index-of k property-k-sort-order) 99))

(defn- truncate [s limit]
  (if (> (count s) limit)
    (str (subs s 0 limit) "...")
    s))

(defmethod ->widget :default [_ v _ctx]
  (->label (truncate (->edn-str v) 60)))

(defmethod widget->value :default [_ widget]
  (actor-id widget))

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
          window (->window {:title "Choose"
                               :modal? true
                               :close-button? true
                               :center? true
                               :close-on-escape? true
                               :cell-defaults {:pad 5}})
          remaining-ks (sort (remove (set (keys (attribute-widget-group->data attribute-widget-group)))
                                     (map-keys (:schema data))))]
      (add-rows! window (for [k remaining-ks]
                             [(->text-button (name k)
                                                (fn [ctx]
                                                  (remove! window)
                                                  (add-actor! attribute-widget-group
                                                                 (->component-widget ctx
                                                                                     [k (get k-props k) (k->default-value k)]
                                                                                     :horizontal-sep?
                                                                                     (pos? (count (children attribute-widget-group)))))
                                                  (pack-ancestor-window! attribute-widget-group)
                                                  ctx))]))
      (.pack window)
      (stage-add! ctx window))))

(declare ->attribute-widget-group)

(defn- optional-keyset [schema]
  (set (map first
            (filter (fn [[k prop-m]] (:optional prop-m))
                    (k-properties schema)))))

(defmethod ->widget :map [[_ data] m ctx]
  (let [attribute-widget-group (->attribute-widget-group ctx (:schema data) m)
        optional-keys-left? (seq (set/difference (optional-keyset (:schema data))
                                                 (set (keys m))))]
    (set-id! attribute-widget-group :attribute-widget-group)
    (->table {:cell-defaults {:pad 5}
                 :rows (remove nil?
                               [(when optional-keys-left?
                                  [(->text-button "Add component"
                                                     (->choose-component-window data attribute-widget-group))])
                                (when optional-keys-left?
                                  [(->horizontal-separator-cell 1)])
                                [attribute-widget-group]])})))


(defmethod widget->value :map [_ table]
  (attribute-widget-group->data (:attribute-widget-group table)))

(defn- ->attribute-label [k]
  (let [label (->label (str k))]
    (when-let [doc (:editor/doc (get component-attributes k))]
      (add-tooltip! label doc))
    label))

(defn- ->component-widget [ctx [k k-props v] & {:keys [horizontal-sep?]}]
  (let [label (->attribute-label k)
        value-widget (->widget (data-component k) v ctx)
        table (->table {:id k :cell-defaults {:pad 4}})
        column (remove nil?
                       [(when (:optional k-props)
                          (->text-button "-" (fn [ctx]
                                                  (let [window (find-ancestor-window table)]
                                                    (remove! table)
                                                    (.pack window))
                                                  ctx)))
                        label
                        (->vertical-separator-cell)
                        value-widget])
        rows [(when horizontal-sep? [(->horizontal-separator-cell (count column))])
              column]]
    (set-id! value-widget v)
    (add-rows! table (remove nil? rows))
    table))

(defn- attribute-widget-table->value-widget [table]
  (-> table children last))

(defn- ->component-widgets [ctx schema props]
  (let [first-row? (atom true)
        k-props (k-properties schema)]
    (for [[k v] (sort-by component-order props)
          :let [sep? (not @first-row?)
                _ (reset! first-row? false)]]
      (->component-widget ctx [k (get k-props k) v] :horizontal-sep? sep?))))

(defn- ->attribute-widget-group [ctx schema props]
  (->vertical-group (->component-widgets ctx schema props)))

(defn- attribute-widget-group->data [group]
  (into {} (for [k (map actor-id (children group))
                 :let [table (k group)
                       value-widget (attribute-widget-table->value-widget table)]]
             [k (widget->value (data-component k) value-widget)])))

;;

(defn- apply-context-fn [window f]
  (fn [ctx]
    (try
     (let [ctx (f ctx)]
       (remove! window)
       ctx)
     (catch Throwable t
       (error-window! ctx t)))))

(defn- ->property-editor-window [ctx id]
  (let [props (safe-get (:db (:context/properties ctx)) id)
        window (->window {:title "Edit Property"
                             :modal? true
                             :close-button? true
                             :center? true
                             :close-on-escape? true
                             :cell-defaults {:pad 5}})
        widgets (->attribute-widget-group ctx (->schema props) props)
        save!   (apply-context-fn window #(update! % (attribute-widget-group->data widgets)))
        delete! (apply-context-fn window #(delete! % id))]
    (add-rows! window [[(->scroll-pane-cell ctx [[{:actor widgets :colspan 2}]
                                                       [(->text-button "Save [LIGHT_GRAY](ENTER)[]" save!)
                                                        (->text-button "Delete" delete!)]])]])
    (add-actor! window (->actor {:act (fn [_ctx]
                                        (when (key-just-pressed? :enter)
                                          (swap! app-state save!)))}))
    (.pack window)
    window))

(defn- ->overview-property-widget [{:keys [property/id] :as props} clicked-id-fn extra-info-text scale]
  (let [on-clicked #(clicked-id-fn % id)
        button (if-let [image (prop->image props)]
                 (->image-button image on-clicked {:scale scale})
                 (->text-button (name id) on-clicked))
        top-widget (->label (or (and extra-info-text (extra-info-text props)) ""))
        stack (->stack [button top-widget])]
    (add-tooltip! button #(->info-text props %))
    (set-touchable! top-widget :disabled)
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
    (->table
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
  (let [main-table (->table {:fill-parent? true})
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
    (.pad (.left (.add main-table (->label "[LIGHT_GRAY]Left-Shift: Back to Main Menu[]"))) (float 10))
    (doseq [tab-data tabs-data]
      (.add tabbed-pane (->tab tab-data)))
    main-table))

(defn- open-property-editor-window! [context property-id]
  (stage-add! context (->property-editor-window context property-id)))

(defn- ->tabs-data [ctx]
  (for [property-type (sort (types))]
    {:title (:title (overview property-type))
     :content (->overview-table ctx property-type open-property-editor-window!)}))

(defsystem ->mk "Create component value. Default returns v." [_ ctx])
(defmethod ->mk :default [[_ v] _ctx] v)

(derive :screens/property-editor :screens/stage)
(defcomponent :screens/property-editor
  (->mk [_ ctx]
    {:stage (->stage ctx [(->background-image ctx)
                          (->tabbed-pane (->tabs-data ctx))
                          (->actor {:act (fn [_ctx]
                                           (when (key-just-pressed? :shift-left)
                                             (swap! app-state change-screen :screens/main-menu)))})])}))

; TODO schemas not checking if that property exists in db...
; https://github.com/damn/core/issues/59


(defn- one-to-many-schema->linked-property-type [[_set [_qualif_kw {:keys [namespace]}]]]
  (ns-k->property-type namespace))

(comment
 (= (one-to-many-schema->linked-property-type [:set [:qualified-keyword {:namespace :items}]])
    :properties/items)
 )

(defmethod edn->value :one-to-many [_ property-ids ctx]
  (map (partial build-property ctx) property-ids))


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
                    (clear-children! table)
                    (add-one-to-many-rows ctx table property-type property-ids)
                    (pack-ancestor-window! table))]
    (add-rows!
     table
     [[(->text-button "+"
                         (fn [ctx]
                           (let [window (->window {:title "Choose"
                                                      :modal? true
                                                      :close-button? true
                                                      :center? true
                                                      :close-on-escape? true})
                                 clicked-id-fn (fn [ctx id]
                                                 (remove! window)
                                                 (redo-rows ctx (conj property-ids id))
                                                 ctx)]
                             (.add window (->overview-table ctx property-type clicked-id-fn))
                             (.pack window)
                             (stage-add! ctx window))))]
      (for [property-id property-ids]
        (let [property (build-property ctx property-id)
              image-widget (->image-widget (prop->image property) {:id property-id})]
          (add-tooltip! image-widget #(->info-text property %))
          image-widget))
      (for [id property-ids]
        (->text-button "-" #(do (redo-rows % (disj property-ids id)) %)))])))

(defmethod ->widget :one-to-many [[_ data] property-ids context]
  (let [table (->table {:cell-defaults {:pad 5}})]
    (add-one-to-many-rows context
                          table
                          (one-to-many-schema->linked-property-type (:schema data))
                          property-ids)
    table))

(defmethod widget->value :one-to-many [_ widget]
  (->> (children widget)
       (keep actor-id)
       set))

(defn- add-one-to-one-rows [ctx table property-type property-id]
  (let [redo-rows (fn [ctx id]
                    (clear-children! table)
                    (add-one-to-one-rows ctx table property-type id)
                    (pack-ancestor-window! table))]
    (add-rows!
     table
     [[(when-not property-id
         (->text-button "+"
                           (fn [ctx]
                             (let [window (->window {:title "Choose"
                                                        :modal? true
                                                        :close-button? true
                                                        :center? true
                                                        :close-on-escape? true})
                                   clicked-id-fn (fn [ctx id]
                                                   (remove! window)
                                                   (redo-rows ctx id)
                                                   ctx)]
                               (.add window (->overview-table ctx property-type clicked-id-fn))
                               (.pack window)
                               (stage-add! ctx window)))))]
      [(when property-id
         (let [property (build-property ctx property-id)
               image-widget (->image-widget (prop->image property) {:id property-id})]
           (add-tooltip! image-widget #(->info-text property %))
           image-widget))]
      [(when property-id
         (->text-button "-" #(do (redo-rows % nil) %)))]])))

(defmethod ->widget :one-to-one [[_ data] property-id ctx]
  (let [table (->table {:cell-defaults {:pad 5}})]
    (add-one-to-one-rows ctx
                         table
                         (one-to-one-schema->linked-property-type (:schema data))
                         property-id)
    table))

(defmethod widget->value :one-to-one [_ widget]
  (->> (children widget) (keep actor-id) first))
