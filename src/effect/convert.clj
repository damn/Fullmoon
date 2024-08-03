(ns effect.convert
  (:require [core.component :refer [defcomponent]]
            [api.effect :as effect]
            [api.entity :as entity]
            [api.tx :refer [transact!]]
            [core.data :as data]))

; TODO cannot be changed from boolean
(defcomponent :effect/convert data/boolean-attr ; duration?! - as modifier - but what if multiple effects - may fuck up changing it back ... ?
  (effect/text [_ _ctx]
    "Converts target to your side.")

  (effect/valid-params? [_ {:keys [effect/source effect/target]}]
    (and target
         (= (:entity/faction @target)
            (entity/enemy-faction @source))))

  (transact! [_ {:keys [effect/source effect/target]}]
    [[:tx.entity/assoc target :entity/faction (entity/friendly-faction @source)]]))

; TODO new property spritesheet where I can choose index not manually
; .. also create new skill button / new property of type

; also how to give player a skill?

; => visualize if entity is on your side / visualize faction

; also start-action-sound stretch to action-time .... :)
; and stop when interrupted ....
; also interrupt skill ....

; also friendly entities block ur movement in engen gaengen ....
; ....

; also should give proper error when called on friendly


; => you have to work from top-level , check  out other games (d2?) and copy items/skills/creatures
; it doesnt work from bottom level ....
; just copy ...
