(ns effect.sound
  (:require [core.component :refer [defcomponent]]
            [api.tx :refer [transact!]]
            [core.data :as data]))

; TODO or derive , is-a ???

(defcomponent :effect/sound data/sound
  (transact! [[_ _effect-ctx sound] _ctx]
    [[:tx/sound sound]]))
