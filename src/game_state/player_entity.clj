(ns game-state.player-entity
  (:require [api.tx :refer [transact!]]))

(defn ->state []
  {:context.game/player-entity nil})

(defmethod transact! :tx.context.game/set-player-entity [[_ entity] ctx]
  (assoc ctx :context.game/player-entity entity))
