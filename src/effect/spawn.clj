(ns effect.spawn
  (:require [core.component :refer [defcomponent]]
            [api.effect :as effect]
            [api.tx :refer [transact!]]))

; TODO spawning on player both without error ?! => not valid position checked
; also what if someone moves on the target posi ? find nearby valid cell ?

; BLOCKING PLAYER MOVEMENT ! (summons no-clip with player ?)
; check not blocked position // line of sight.
; limit max. spawns
; animation/sound
; proper icon (grayscaled ?)
; keep in player movement range priority ( follow player if too far, otherwise going for enemies)
; => so they follow you around

; not try-spawn, but check valid-params & then spawn !

; new UI -> show creature body & then place
; >> but what if it is blocked the area during action-time ?? <<

; Also: to make a complete game takes so many creatures, items, skills, balance, ui changes, testing
; is it even possible ?

(comment
 ; keys: :faction(:source)/:target-position/:creature-id
 )

; => one to one attr!?
(defcomponent :effect/spawn {:widget :text-field
                             :schema [:qualified-keyword {:namespace :creatures}]}
  (effect/text [[_ creature-id] _ctx]
    (str "Spawns a " (name creature-id)))

  (effect/valid-params? [_ {:keys [effect/source
                                   effect/target-position]}]
    ; TODO line of sight ? / not blocked ..
    (and source
         (:entity/faction @source)
         target-position))

  (transact! [[_ creature-id] {:keys [effect/source
                                      effect/target-position] :as ctx}]
    [[:tx.entity/creature
      creature-id
      #:entity {:position target-position
                :state [:state/npc :idle]
                :faction (:entity/faction @source)}]]))
