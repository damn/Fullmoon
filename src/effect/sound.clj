(ns effect.sound
  (:require [core.component :as component]
            [api.tx :refer [transact!]]
            [core.data :as attr]))

; TODO or derive , is-a ???

(component/def :effect/sound attr/sound
  sound
  (transact! [_ ctx]
    [[:tx/sound sound]]))
