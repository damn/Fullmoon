(ns entity.flying
  (:require [core.component :as component]
            [data.types :as attr]))

(component/def :entity/flying? attr/boolean-attr) ; optional, mixed with z-order
