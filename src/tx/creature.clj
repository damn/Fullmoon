(ns tx.creature
  (:require [api.context :as ctx]
            [api.tx :refer [transact!]]
            [entity-state.player :as player-state]
            [entity-state.npc :as npc-state]))

; TODO dependencies as of entity-state components -> move them also out of entity/ folder
; body
; skills
; mana
; stats (cast,attack-speed -> move to skills?)
; movement (if should move w. movement-vector ?!)

; npc:
; reaction-time
; faction

; player:
; click-distance-tiles
; free-skill-points
; inventory
; item-on-cursor (added by itself)

(defn- set-state [[player-or-npc initial-state]]
  ((case player-or-npc
     :state/player player-state/->state
     :state/npc npc-state/->state)
   initial-state))

(defmethod transact! :tx/creature [[_ creature-id extra-components] ctx]
  (assert (:entity/state extra-components))
  (let [property-components (:creature/entity (ctx/get-property ctx creature-id))]
    [[:tx/create (merge property-components
                        (update extra-components :entity/state set-state)
                        {:entity/z-order (if (:entity/flying? property-components)
                                           :z-order/flying
                                           :z-order/ground)}
                        (when (= creature-id :creatures/lady-a)
                          {:entity/clickable {:type :clickable/princess}}))]]))
