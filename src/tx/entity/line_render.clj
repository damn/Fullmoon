(ns tx.entity.line-render
  (:require [api.tx :refer [transact!]]))

(defmethod transact! :tx.entity/line-render [[_ {:keys [start end duration color thick?]}] _ctx]
  [[:tx/create #:entity {:body {:position start
                                :width 0.5
                                :height 0.5}
                         :z-order :z-order/effect
                         :line-render {:thick? thick? :end end :color color}
                         :delete-after-duration duration}]])
