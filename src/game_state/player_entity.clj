(ns game-state.player-entity
  (:require [api.tx :refer [transact!]]))

(defn ->state []
  {:player-entity nil})

(defmethod transact! :tx.context.game/set-player-entity [[_ entity] ctx]
  (swap! (:context/game ctx) assoc :player-entity entity)
  ctx)
