(ns tx.entity.line-render
  (:require [api.tx :refer [transact!]]))

(defmethod transact! :tx.entity/line-render [[_ {:keys [start end duration color thick?]}] _ctx]
  [[:tx/create #:entity {:position start
                         :z-order :z-order/effect
                         :line-render {:thick? thick? :end end :color color}
                         :delete-after-duration duration}]])
