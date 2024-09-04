(ns components.properties.item
  (:require [clojure.string :as str]
            [core.component :as component :refer [defcomponent]]
            [core.inventory :as inventory]
            [core.modifiers :as modifiers]))

(defcomponent :item/modifiers
  {:data [:components-ns :modifier]
   :let modifiers}
  (component/info-text [_ _ctx]
    (when (seq modifiers)
      (modifiers/info-text modifiers))))

(defcomponent :item/slot
  {:data [:enum (keys inventory/empty-inventory)]})

(defcomponent :properties/items
  (component/create [_ _ctx]
    {:schema [:property/pretty-name
              :entity/image
              :item/slot
              [:item/modifiers {:optional true}]]
     :overview {:title "Items"
                :columns 20
                :image/scale 1.1
                :sort-by-fn #(vector (if-let [slot (:item/slot %)]
                                       (name slot)
                                       "")
                                     (name (:property/id %)))}}))

; TODO use image w. shadows spritesheet
(defcomponent :tx/item
  (component/do! [[_ position item] _ctx]
    (assert (:entity/image item))
    [[:tx/create
      position
      {:width 0.5 ; TODO use item-body-dimensions
       :height 0.5
       :z-order :z-order/on-ground}
      #:entity {:image (:entity/image item)
                :item item
                :clickable {:type :clickable/item
                            :text (:property/pretty-name item)}}]]))
