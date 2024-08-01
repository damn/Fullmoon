(ns effect.sound
  (:require [core.component :refer [defcomponent]]
            [api.tx :refer [transact!]]
            [core.data :as attr]))

; TODO or derive , is-a ???

(defcomponent :effect/sound attr/sound
  (transact! [[_ sound] ctx]
    [[:tx/sound sound]]))
