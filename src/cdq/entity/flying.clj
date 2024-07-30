(ns cdq.entity.flying
  (:require [core.component :as component]
            [cdq.attributes :as attr]))

(component/def :entity/flying? attr/boolean-attr) ; optional, mixed with z-order
