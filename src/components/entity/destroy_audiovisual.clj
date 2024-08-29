(ns components.entity.destroy-audiovisual
  (:require [core.component :as component :refer [defcomponent]]))

(defcomponent :entity/destroy-audiovisual
  {:let audiovisuals-id}
  (component/destroy-e [_ entity ctx]
    [[:tx/audiovisual (:position @entity) audiovisuals-id]]))
