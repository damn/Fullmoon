(ns tx.entity.item
  (:require [api.tx :refer [transact!]]))

; TODO use image w. shadows spritesheet
(defmethod transact! :tx.entity/item  [[_ position item] _ctx]
  [[:tx/create #:entity {:position position
                         :body {:width 0.5 ; TODO use item-body-dimensions
                                :height 0.5
                                :solid? false}
                         :z-order :z-order/on-ground
                         :image (:property/image item)
                         :item item
                         :clickable {:type :clickable/item
                                     :text (:property/pretty-name item)}}]])
