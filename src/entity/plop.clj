(ns entity.plop
  (:require [core.component :refer [defcomponent]]
            [api.entity :as entity]))

(defcomponent :entity/plop {} _
  (entity/destroy [_ entity* ctx]
    [[:tx/audiovisual (:entity/position entity*) :audiovisuals/hit-wall]]))
