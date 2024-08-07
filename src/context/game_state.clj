(ns context.game-state
  (:require [core.component :refer [defcomponent] :as component]
            [game-state.mouseover-entity :as mouseover-entity]
            [game-state.elapsed-time :as elapsed-time]
            [game-state.ecs :as ecs]))

; TODO also delta-time, player-entity, world, replay-mode
; then componet based do it
; then move the atom out ....
; TODO also transaction-handler.....
(defcomponent :context/game-state {}
  (component/create [_ _ctx]
    (merge {:paused? (atom nil)
            :logic-frame (atom 0)}
           (ecs/->state)
           (elapsed-time/->state)
           (mouseover-entity/->state))))
