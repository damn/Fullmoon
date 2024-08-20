(ns entity.player
  (:require [core.component :refer [defcomponent]]
            [api.context :as ctx]
            [api.entity :as entity]))

(defcomponent :entity/player? {}
  (entity/create [_ eid _ctx]
    (assoc ctx ::eid eid)))

(extend-type api.context.Context
  api.context/PlayerEntity
  (player-entity  [ctx] (::eid ctx))
  (player-entity* [ctx] @(ctx/player-entity ctx)))
