(in-ns 'world.entity.stats)

; TODO negate this value also @ use
; so can make positiive modifeirs green , negative red....
(defmodifier :modifier/damage-receive [:op/max-inc :op/max-mult])
(defmodifier :modifier/damage-deal [:op/val-inc :op/val-mult :op/max-inc :op/max-mult])

; TODO needs to be there for each npc - make non-removable (added to all creatures)
; and no need at created player (npc controller component?)
(defstat :stats/aggro-range   {:schema nat-int?})
(defstat :stats/reaction-time {:schema pos-int?})

; TODO
; @ hp says here 'Minimum' hp instead of just 'HP'
; Sets to 0 but don't kills
; Could even set to a specific value ->
; op/set-to-ratio 0.5 ....
; sets the hp to 50%...
(defstat :stats/hp
  {:schema pos-int?
   :modifier-ops [:op/max-inc :op/max-mult]
   :effect-ops [:op/val-inc :op/val-mult :op/max-inc :op/max-mult]})

(defstat :stats/mana
  {:schema nat-int?
   :modifier-ops [:op/max-inc :op/max-mult]
   :effect-ops [:op/val-inc :op/val-mult :op/max-inc :op/max-mult]})

(defc :tx.entity.stats/pay-mana-cost
  (tx/do! [[_ eid cost]]
    (let [mana-val ((entity-stat @eid :stats/mana) 0)]
      (assert (<= cost mana-val))
      [[:e/assoc-in eid [:entity/stats :stats/mana 0] (- mana-val cost)]])))

(comment
 (let [mana-val 4
       eid (atom (entity/map->Entity {:entity/stats {:stats/mana [mana-val 10]}}))
       mana-cost 3
       resulting-mana (- mana-val mana-cost)]
   (= (tx/do! [:tx.entity.stats/pay-mana-cost eid mana-cost] nil)
      [[:e/assoc-in eid [:entity/stats :stats/mana 0] resulting-mana]]))
 )

; * TODO clamp/post-process effective-values @ stat-k->effective-value
; * just don't create movement-speed increases too much?
; * dont remove strength <0 or floating point modifiers  (op/int-inc ?)
; * cast/attack speed dont decrease below 0 ??

; TODO clamp between 0 and max-speed ( same as movement-speed-schema )
(defstat :stats/movement-speed
  {:schema pos? ;(m/form entity/movement-speed-schema)
   :modifier-ops [:op/inc :op/mult]})

; TODO show the stat in different color red/green if it was permanently modified ?
; or an icon even on the creature
; also we want audiovisuals always ...
(defc :effect.entity/movement-speed
  {:schema [:s/map [:op/mult]]})
(derive :effect.entity/movement-speed :base/stat-effect)

; TODO clamp into ->pos-int
(defstat :stats/strength
  {:schema nat-int?
   :modifier-ops [:op/inc]})

; TODO here >0
(let [doc "action-time divided by this stat when a skill is being used.
          Default value 1.

          For example:
          attack/cast-speed 1.5 => (/ action-time 1.5) => 150% attackspeed."
      schema pos?
      operations [:op/inc]]
  (defstat :stats/cast-speed
    {:schema schema
     :editor/doc doc
     :modifier-ops operations})

  (defstat :stats/attack-speed
    {:schema schema
     :editor/doc doc
     :modifier-ops operations}))

; TODO bounds
(defstat :stats/armor-save
  {:schema number?
   :modifier-ops [:op/inc]})

(defstat :stats/armor-pierce
  {:schema number?
   :modifier-ops [:op/inc]})

