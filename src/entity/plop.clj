(ns entity.plop
  (:require [core.component :refer [defcomponent]]
            [api.entity :as entity]))

(defcomponent :entity/plop {}
  (entity/destroy [_ entity* ctx]
    [[:tx.entity/audiovisual (entity/position entity*) :audiovisuals/hit-wall]]))


; Projectile dies ways:
; * (and hit-entity (not piercing?))
; * hits-wall?
; * duration/maxrange finished


; * on hits wall is not even rendered ....
; isn't it audiovisual ????
; effect always rendered?

; maybe projectile-collision trigger @ body
; before even moved to that loc ?

; also enemies are attacking my projectile with bow !!
; its not a valid target for such effects ....

; why when shooting left wall we have LoS and right wall not ?
; =-> but its minor bug for now ...

; I DO see the first walls around ... so they are in lOs!

; => bug report....

; Problably we want a simple state-machine simple for proejctiles
; where on kill this is triggered
; so we can remove projectiles also / interact with them

; or we do it with destroy idk.
