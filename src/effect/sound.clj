(ns effect.sound
  (:require [core.component :refer [defcomponent]]
            [core.data :as data]
            ;[api.effect :as effect]
            [api.tx :refer [transact!]]))

(defcomponent :effect/sound data/sound
  (transact! [[_ sound] _effect-ctx]
    [[:tx/sound sound]]))
