(in-ns 'editor.widgets)

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
                           (.add window (overview-table property-type clicked-id-fn))
                           (.pack window)
                           (stage-add! window))))]
      (for [property-id property-ids]
        (let [property (db/get property-id)
              image-widget (ui/image->widget (property/->image property) {:id property-id})]
          (ui/add-tooltip! image-widget #(info/->text property))
          image-widget))
      (for [id property-ids]
        (ui/text-button "-" #(redo-rows (disj property-ids id))))])))

(defmethod widget/create :s/one-to-many [[_ property-type] property-ids]
  (let [table (ui/table {:cell-defaults {:pad 5}})]
    (add-one-to-many-rows table property-type property-ids)
    table))

(defmethod widget/value :s/one-to-many [_ widget]
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
                             (.add window (overview-table property-type clicked-id-fn))
                             (.pack window)
                             (stage-add! window)))))]
      [(when property-id
         (let [property (db/get property-id)
               image-widget (ui/image->widget (property/->image property) {:id property-id})]
           (ui/add-tooltip! image-widget #(info/->text property))
           image-widget))]
      [(when property-id
         (ui/text-button "-" #(redo-rows nil)))]])))

(defmethod widget/create :s/one-to-one [[_ property-type] property-id]
  (let [table (ui/table {:cell-defaults {:pad 5}})]
    (add-one-to-one-rows table property-type property-id)
    table))

(defmethod widget/value :s/one-to-one [_ widget]
  (->> (ui/children widget) (keep a/id) first))
