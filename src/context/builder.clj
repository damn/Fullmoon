(ns context.builder
  (:require api.context))

(extend-type api.context.Context
  api.context/Builder
  ; TODO use image w. shadows spritesheet
  (item-entity [_ position item]
    #:entity {:position position
              :body {:width 0.5 ; TODO use item-body-dimensions
                     :height 0.5
                     :solid? false}
              :z-order :z-order/on-ground
              :image (:property/image item)
              :item item
              :clickable {:type :clickable/item
                          :text (:property/pretty-name item)}})

  (line-entity [_ {:keys [start end duration color thick?]}]
    #:entity {:position start
              :z-order :z-order/effect
              :line-render {:thick? thick? :end end :color color}
              :delete-after-duration duration}))
