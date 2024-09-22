(ns core.context)

(defrecord Context [])

(defprotocol World
  (active-entities [_])
  (world-grid [_]))

; core.property.types.world ?
(defprotocol WorldGenerator
  (->world [ctx world-id]))
