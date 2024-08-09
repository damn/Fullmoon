(ns tx.entity.shout
  (:require [api.context :as ctx]
            [api.tx :refer [transact!]]))

(defmethod transact! :tx.entity/shout [[_ position faction delay-seconds] ctx]
  [[:tx/create #:entity {:body {:position position
                                :width 0.5
                                :height 0.5}
                         :faction faction
                         :shout (ctx/->counter ctx delay-seconds)}]])
