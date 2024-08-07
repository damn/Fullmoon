(ns game-state.player-entity
  (:require [api.tx :refer [transact!]]))

(defmethod transact! :tx.context.game/set-player-entity [[_ entity] ctx]
  (reset! (:player-entity-ref (:context/game ctx)) entity)
  nil)

(defn ->state []
  ; a reference to an entity which is again a reference
  {:player-entity-ref (atom nil)})
