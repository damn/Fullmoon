(ns context.game.player-entity
  (:require [api.context :as ctx]
            [api.effect :as effect]))

(extend-type api.context.Context
  api.context/PlayerEntity
  (player-entity  [ctx] (:context.game/player-entity ctx))
  (player-entity* [ctx] @(ctx/player-entity ctx)))

(defmethod effect/do! :tx.context.game/set-player-entity [[_ entity] ctx]
  (assoc ctx :context.game/player-entity entity))
