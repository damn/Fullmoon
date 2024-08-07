(ns entity.player
  (:require [core.component :refer [defcomponent]]
            [api.entity :as entity]))

(defcomponent :entity/player? {}
  (entity/create [_ entity* _ctx]
    [[:tx.context.game/set-player-entity (:entity/id entity*)]]))


