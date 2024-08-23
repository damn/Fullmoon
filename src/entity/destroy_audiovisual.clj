(ns entity.destroy-audiovisual
  (:require [core.component :refer [defcomponent]]
            [api.entity :as entity]))

(defcomponent :entity/destroy-audiovisual {}
  (entity/destroy [[_ audiovisuals-id] entity ctx]
    [[:tx.entity/audiovisual (:position @entity) audiovisuals-id]]))
