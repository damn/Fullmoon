(ns entity.z-order
  (:require [core.component :refer [defcomponent]]
            [core.data :as data]
            [api.entity :as entity]))

(defcomponent :entity/z-order (apply data/enum entity/z-orders))

(extend-type api.entity.Entity
  entity/ZOrder
  (z-order [entity*] (:entity/z-order entity*)))
