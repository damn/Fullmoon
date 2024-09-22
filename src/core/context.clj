(ns core.context)

(defrecord Context [])

(defprotocol World
  (active-entities [_])
  (world-grid [_]))

; skills & effects together = 'core.action' ?
(defprotocol ActiveSkill
  (skill-usable-state [ctx entity* skill]))

; core.property.types.world ?
(defprotocol WorldGenerator
  (->world [ctx world-id]))
