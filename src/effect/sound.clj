(ns effect.sound
  (:require [core.component :as component]
            [api.context :refer [transact!]]
            [data.types :as attr]))

; TODO or derive , is-a ???

(component/def :effect/sound attr/sound
  sound
  (transact! [_ ctx]
    [[:tx/sound sound]]))
