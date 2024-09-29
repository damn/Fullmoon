(in-ns 'core.entity)

(defcomponent :entity/destroy-audiovisual
  {:let audiovisuals-id}
  (destroy [_ entity ctx]
    [[:tx/audiovisual (:position @entity) audiovisuals-id]]))
