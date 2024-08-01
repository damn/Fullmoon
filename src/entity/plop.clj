(ns entity.plop
  (:require [core.component :as component]
            [api.entity :as entity]))

(component/def :entity/plop {} _
  (entity/destroy [_ entity* ctx]
    [[:tx/audiovisual (:entity/position entity*) :audiovisuals/hit-wall]]))
