(ns entity.destroy-audiovisual
  (:require [core.component :refer [defcomponent]]
            [core.entity :as entity]))

(defcomponent :entity/destroy-audiovisual {}
  audiovisuals-id
  (entity/destroy [_ entity ctx]
    [[:tx.entity/audiovisual (:position @entity) audiovisuals-id]]))
