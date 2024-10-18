(ns world.effect.entity
  (:require [component.core :refer [defc]]
            [component.info :as info]
            [component.tx :as tx]
            [gdx.graphics :as g]
            [utils.core :refer [readable-number]]
            [world.core :refer [timer stopped? finished-ratio]]
            [world.entity :as entity]
            [world.effect :as effect :refer [source target]]))

(defc :entity/temp-modifier
  {:let {:keys [counter modifiers]}}
  (info/text [_]
    (str "[LIGHT_GRAY]Spiderweb - remaining: " (readable-number (finished-ratio counter)) "/1[]"))

  (entity/tick [[k _] eid]
    (when (stopped? counter)
      [[:e/dissoc eid k]
       [:tx/reverse-modifiers eid modifiers]]))

  (entity/render-above [_ entity]
    (g/draw-filled-circle (:position entity) 0.5 [0.5 0.5 0.5 0.4])))

(let [modifiers {:modifier/movement-speed {:op/mult -0.5}}
      duration 5]
  (defc :effect.entity/spiderweb
    {:schema :some}
    (info/text [_]
      "Spiderweb slows 50% for 5 seconds."
      ; modifiers same like item/modifiers has info-text
      ; counter ?
      )

    (effect/applicable? [_]
      ; ?
      true)

    ; TODO stacking? (if already has k ?) or reset counter ? (see string-effect too)
    (tx/do! [_]
      (when-not (:entity/temp-modifier @target)
        [[:tx/apply-modifiers target modifiers]
         [:e/assoc target :entity/temp-modifier {:modifiers modifiers
                                                 :counter (timer duration)}]]))))

(defc :effect.entity/convert
  {:schema :some}
  (info/text [_]
    "Converts target to your side.")

  (effect/applicable? [_]
    (and target
         (= (:entity/faction @target)
            (entity/enemy @source))))

  (tx/do! [_]
    [[:tx/audiovisual (:position @target) :audiovisuals/convert]
     [:e/assoc target :entity/faction (entity/friend @source)]]))

(defc :effect.entity/stun
  {:schema pos?
   :let duration}
  (info/text [_]
    (str "Stuns for " (readable-number duration) " seconds"))

  (effect/applicable? [_]
    (and target (:entity/state @target)))

  (tx/do! [_]
    [[:tx/event target :stun duration]]))

(defc :effect.entity/kill
  {:schema :some}
  (info/text [_] "Kills target")

  (effect/applicable? [_]
    (and target (:entity/state @target)))

  (tx/do! [_]
    [[:tx/event target :kill]]))
