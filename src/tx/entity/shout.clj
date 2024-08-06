(ns tx.entity.shout
  (:require [api.context :as ctx]
            [api.tx :refer [transact!]]))

(defmethod transact! :tx.entity/shout [[_ position faction delay-seconds] ctx]
  [[:tx/create #:entity {:position position
                         :faction faction
                         :shout (ctx/->counter ctx delay-seconds)}]])
