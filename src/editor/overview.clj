(ns editor.overview
  (:require [component.db :as db]
            [component.info :as info]
            [component.property :as property]
            [gdx.ui :as ui]
            [gdx.ui.actor :as a]))

(defn- property-widget [{:keys [property/id] :as props} clicked-id-fn extra-info-text scale]
  (let [on-clicked #(clicked-id-fn id)
        button (if-let [image (property/->image props)]
                 (ui/image-button image on-clicked {:scale scale})
                 (ui/text-button (name id) on-clicked))
        top-widget (ui/label (or (and extra-info-text (extra-info-text props)) ""))
        stack (ui/stack [button top-widget])]
    (ui/add-tooltip! button #(info/->text props))
    (a/set-touchable! top-widget :disabled)
    stack))

(defn overview-table [property-type clicked-id-fn]
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
                (try (property-widget property clicked-id-fn extra-info-text scale)
                     (catch Throwable t
                       (throw (ex-info "" {:property property} t))))))})))
