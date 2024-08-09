(ns entity.plop
  (:require [core.component :refer [defcomponent]]
            [api.entity :as entity]))

(defcomponent :entity/plop {}
  (entity/destroy [_ entity* ctx]
    [[:tx.entity/audiovisual (entity/position entity*) :audiovisuals/hit-wall]]))
