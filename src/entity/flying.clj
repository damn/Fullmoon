(ns entity.flying
  (:require [core.component :refer [defcomponent]]
            [core.data :as attr]))

(defcomponent :entity/flying? attr/boolean-attr) ; optional, mixed with z-order
