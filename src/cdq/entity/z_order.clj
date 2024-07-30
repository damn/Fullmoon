(ns cdq.entity.z-order
  (:require [core.component :as component]
            [cdq.attributes :as attr]))

(component/def :entity/z-order (attr/enum :z-order/on-ground
                                          :z-order/ground
                                          :z-order/flying
                                          :z-order/effect))
