(ns components.properties.item
  (:require [core.component :as component :refer [defcomponent]]
            [core.inventory :as inventory]
            [core.modifiers :as modifiers]
            [core.property :refer [def-property-type]]))

(defcomponent :item/modifiers
  {:data [:components-ns :modifier]
   :let modifiers}
  (component/info-text [_ _ctx]
    (when (seq modifiers)
      (modifiers/info-text modifiers))))

(defcomponent :item/slot
  {:data [:enum (keys inventory/empty-inventory)]})

(def-property-type :properties/items
  {:attributes [:property/pretty-name
                :entity/image
                :item/slot
                [:item/modifiers {:optional true}]]
   :overview {:title "Items"
              :columns 20
              :image/scale 1.1
              :sort-by-fn #(vector (if-let [slot (:item/slot %)]
                                     (name slot)
                                     "")
                             (name (:property/id %)))}})

(def ^:private body-props
  {:width 0.75
   :height 0.75
   :z-order :z-order/on-ground})

(defcomponent :tx/item
  (component/do! [[_ position item] _ctx]
    [[:tx/create position body-props {:entity/image (:entity/image item)
                                      :entity/item item
                                      :entity/clickable {:type :clickable/item
                                                         :text (:property/pretty-name item)}}]]))
