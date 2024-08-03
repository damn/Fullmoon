(ns entity.flying
  (:require [core.component :refer [defcomponent]]
            [core.data :as data]))

(defcomponent :entity/flying? data/boolean-attr) ; optional, mixed with z-order
