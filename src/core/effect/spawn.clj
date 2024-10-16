(ns core.effect.spawn
  (:require [core.component :refer [defc]]
            [core.effect :as effect :refer [source target-position]]
            [core.tx :as tx]))

; https://github.com/damn/core/issues/29
; spawning on player both without error ?! => not valid position checked
; also what if someone moves on the target posi ? find nearby valid cell ?
; BLOCKING PLAYER MOVEMENT ! (summons no-clip with player ?)
; check not blocked position // line of sight. (part of target-position make)
; limit max. spawns
; animation/sound
; proper icon (grayscaled ?)
; keep in player movement range priority ( follow player if too far, otherwise going for enemies)
; => so they follow you around
; not try-spawn, but check valid-params & then spawn !
; new UI -> show creature body & then place
; >> but what if it is blocked the area during action-time ?? <<
(defc :effect/spawn
  {:db/schema [:one-to-one :properties/creatures]
   :let {:keys [property/id]}}
  (effect/applicable? [_]
    (and (:entity/faction @source)
         target-position))

  (tx/do! [_]
    [[:tx/sound "sounds/bfxr_shield_consume.wav"]
     [:tx/creature {:position target-position
                    :creature-id id ; already properties/get called through one-to-one, now called again.
                    :components {:entity/state [:state/npc :npc-idle]
                                 :entity/faction (:entity/faction @source)}}]]))
