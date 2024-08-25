(ns components.properties.item
  (:require [clojure.string :as str]
            [core.component :as component :refer [defcomponent]]
            [core.modifiers :as modifiers]))

(com.badlogic.gdx.graphics.Colors/put "ITEM_GOLD"
                                      (com.badlogic.gdx.graphics.Color. (float 0.84)
                                                                        (float 0.8)
                                                                        (float 0.52)
                                                                        (float 1)))

(defcomponent :properties/item
  (component/create [_ _ctx]
    {:id-namespace "items"
     :schema [[:property/id [:qualified-keyword {:namespace :items}]]
              [:property/pretty-name
               :property/image
               :item/slot
               :item/modifiers]]
     :edn-file-sort-order 3
     :overview {:title "Items"
                :columns 17
                :image/dimensions [60 60]
                :sort-by-fn #(vector (if-let [slot (:item/slot %)]
                                       (name slot)
                                       "")
                                     (name (:property/id %)))}
     :->text (fn [ctx
                  {:keys [property/pretty-name
                          item/modifiers]
                   :as item}]
               [(str "[ITEM_GOLD]" pretty-name (when-let [cnt (:count item)] (str " (" cnt ")")) "[]")
                (when (seq modifiers)
                  (modifiers/info-text modifiers))])}))

; TODO use image w. shadows spritesheet
(defcomponent :tx.entity/item
  (component/do! [[_ position item] _ctx]
    (assert (:property/image item))
    [[:tx/create
      {:position position
       :width 0.5 ; TODO use item-body-dimensions
       :height 0.5
       :z-order :z-order/on-ground}
      #:entity {:image (:property/image item)
                :item item
                :clickable {:type :clickable/item
                            :text (:property/pretty-name item)}}]]))
