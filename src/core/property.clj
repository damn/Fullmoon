(ns core.property
  (:require [clojure.edn :as edn]
            clojure.pprint
            [malli.core :as m]
            [malli.error :as me]
            [core.utils.core :as utils]
            [core.ctx :refer :all]
            [core.ui :as ui])
  (:import (com.kotcrab.vis.ui.widget VisCheckBox VisSelectBox VisTextField)))

(defsystem ->value "..." [_])

(defn data-component [k]
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

(defn property-type->id-namespace [property-type]
  (keyword (name property-type)))

(defn ns-k->property-type [ns-k]
  (keyword "properties" (name ns-k)))

(defn- ->type [{:keys [property/id]}]
  (keyword "properties" (namespace id)))

(defn ->image [{:keys [entity/image entity/animation]}] ; FIXME replaces ctx var
  (or image
      (first (:frames animation))))

(defn types []
  (filter #(= "properties" (namespace %)) (keys component-attributes)))

(defn overview [property-type]
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

(defmethod ->widget :number [[_ data] v _ctx]
  (add-schema-tooltip! (ui/->text-field (utils/->edn-str v) {})
                       data))

(defmethod widget->value :number [_ widget]
  (edn/read-string (.getText ^VisTextField widget)))

(defcomponent :enum
  (->value [[_ items]]
    {:schema (apply vector :enum items)}))

(defmethod ->widget :enum [[_ data] v _ctx]
  (ui/->select-box {:items (map utils/->edn-str (rest (:schema data)))
                    :selected (utils/->edn-str v)}))

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
