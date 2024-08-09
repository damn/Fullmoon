(ns effect.sound
  (:require [core.component :refer [defcomponent]]
            [core.data :as data]
            [api.effect :as effect]))

(defcomponent :effect/sound data/sound
  (effect/txs [[_ sound] _effect-ctx]
    [[:tx/sound sound]]))
