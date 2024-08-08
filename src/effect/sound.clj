(ns effect.sound
  (:require [core.component :refer [defcomponent]]
            [core.data :as data]
            [api.effect :as effect]))

; TODO or derive , is-a ???

(defcomponent :effect/sound data/sound
  (effect/txs [[_ sound] _effect-ctx]
    [[:tx/sound sound]]))
