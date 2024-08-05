(ns tx.creature
  (:require [api.context :as ctx]
            [api.tx :refer [transact!]]
            [entity-state.player :as player-state]
            [entity-state.npc :as npc-state]))

; TODO @ properties.creature set optional/obligatory .... what is needed ???
; body
; skills
; mana
; stats (cast,attack-speed -> move to skills?)
; movement (if should move w. movement-vector ?!, otherwise still in 'moving' state ... )

; npc:
; reaction-time
; faction

; player:
; click-distance-tiles
; free-skill-points
; inventory
; item-on-cursor (added by itself)


;;;; add 'controller'
; :type controller/npc or controller/player
;;; dissoc here and assign components ....
; only npcs need reaction time ....

(defn- set-state [[player-or-npc initial-state]]
  ((case player-or-npc
     :state/player player-state/->state
     :state/npc npc-state/->state)
   initial-state))

; if controller = :controller/player
; -> add those fields
; :player? true ; -> api -> 'entity/player?' fn
; :free-skill-points 3
; :clickable {:type :clickable/player}
; :click-distance-tiles 1.5

; otherwise

(defmethod transact! :tx/creature [[_ creature-id components] ctx]
  (assert (:entity/state components))
  (let [creature-components (:creature/entity (ctx/get-property ctx creature-id))]
    [[:tx/create (merge creature-components
                        (update components :entity/state set-state)
                        {:entity/z-order (if (:entity/flying? creature-components)
                                           :z-order/flying
                                           :z-order/ground)}
                        (when (= creature-id :creatures/lady-a)
                          {:entity/clickable {:type :clickable/princess}}))]]))
