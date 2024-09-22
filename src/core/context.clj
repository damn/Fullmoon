(ns core.context)

(defrecord Context [])

(defprotocol World
  (active-entities [_])
  (world-grid [_]))
