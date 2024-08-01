(ns entity.z-order
  (:require [core.component :refer [defcomponent]]
            [core.data :as attr]))

(defcomponent :entity/z-order (attr/enum :z-order/on-ground
                                          :z-order/ground
                                          :z-order/flying
                                          :z-order/effect))
