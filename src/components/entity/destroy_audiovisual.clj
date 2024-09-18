(ns components.entity.destroy-audiovisual
  (:require [core.component :refer [defcomponent]]
            [core.entity :as entity]))

(defcomponent :entity/destroy-audiovisual
  {:let audiovisuals-id}
  (entity/destroy [_ entity ctx]
    [[:tx/audiovisual (:position @entity) audiovisuals-id]]))
