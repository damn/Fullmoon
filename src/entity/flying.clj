(ns entity.flying
  (:require [core.component :as component]
            [core.data :as attr]))

(component/def :entity/flying? attr/boolean-attr) ; optional, mixed with z-order
