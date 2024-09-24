(ns ^:no-doc core.entity.destroy-audiovisual
  (:require [core.ctx :refer :all]
            [core.entity :as entity]))

(defcomponent :entity/destroy-audiovisual
  {:let audiovisuals-id}
  (entity/destroy [_ entity ctx]
    [[:tx/audiovisual (:position @entity) audiovisuals-id]]))
