(ns entity.z-order
  (:require [core.component :refer [defcomponent]]
            [core.data :as data]))

(defcomponent :entity/z-order (data/enum
                                :z-order/on-ground
                                :z-order/ground
                                :z-order/flying
                                :z-order/effect))
