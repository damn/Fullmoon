(ns components.effect-entity.damage
  (:require [utils.random :as random]
            [core.component :as component :refer [defcomponent defcomponent*]]
            [core.entity :as entity]
            [core.modifier :refer [defmodifier]]))

; TODO negate this value also @ use
; so can make positiive modifeirs green , negative red....
(defmodifier :modifier/damage-receive [:op/max-inc :op/max-mult])
(defmodifier :modifier/damage-deal [:op/val-inc :op/val-mult :op/max-inc :op/max-mult])

(defn- entity*->melee-damage [entity*]
  (let [strength (or (entity/stat entity* :stats/strength) 0)]
    {:damage/min-max [strength strength]}))

(defn- damage-effect [{:keys [effect/source]}]
  [:effect.entity/damage (entity*->melee-damage @source)])

(defcomponent :effect.entity/melee-damage
  {:data :some}
  (component/info-text [_ {:keys [effect/source] :as effect-ctx}]
    (str "Damage based on entity strength."
         (when source
           (str "\n" (component/info-text (damage-effect effect-ctx)
                                          effect-ctx)))))

  (component/applicable? [_ effect-ctx]
    (component/applicable? (damage-effect effect-ctx) effect-ctx))

  (component/do! [_ ctx]
    [(damage-effect ctx)]))

(defn- effective-armor-save [source* target*]
  (max (- (or (entity/stat target* :stats/armor-save) 0)
          (or (entity/stat source* :stats/armor-pierce) 0))
       0))

(comment
 ; broken
 (let [source* {:entity/stats {:stats/armor-pierce 0.4}}
       target* {:entity/stats {:stats/armor-save   0.5}}]
   (effective-armor-save source* target*))
 )

(defn- armor-saves? [source* target*]
  (< (rand) (effective-armor-save source* target*)))

(defn- ->effective-damage [damage source*]
  (update damage :damage/min-max #(entity/->modified-value source* :modifier/damage-deal %)))

(comment
 (let [->source (fn [mods] {:entity/modifiers mods})]
   (and
    (= (->effective-damage {:damage/min-max [5 10]}
                           (->source {:modifier/damage-deal {:op/val-inc [1 5 10]
                                                            :op/val-mult [0.2 0.3]
                                                            :op/max-mult [1]}}))
       #:damage{:min-max [31 62]})

    (= (->effective-damage {:damage/min-max [5 10]}
                           (->source {:modifier/damage-deal {:op/val-inc [1]}}))
       #:damage{:min-max [6 10]})

    (= (->effective-damage {:damage/min-max [5 10]}
                           (->source {:modifier/damage-deal {:op/max-mult [2]}}))
       #:damage{:min-max [5 30]})

    (= (->effective-damage {:damage/min-max [5 10]}
                           (->source nil))
       #:damage{:min-max [5 10]}))))

(defn- damage->text [{[min-dmg max-dmg] :damage/min-max}]
  (str min-dmg "-" max-dmg " damage"))

(defcomponent :damage/min-max {:data :val-max})

(defcomponent :effect.entity/damage
  {:let damage
   :data [:map [:damage/min-max]]}
  (component/info-text [_ {:keys [effect/source]}]
    (if source
      (let [modified (->effective-damage damage @source)]
        (if (= damage modified)
          (damage->text damage)
          (str (damage->text damage) "\nModified: " (damage->text modified))))
      (damage->text damage))) ; property menu no source,modifiers

  (component/applicable? [_ {:keys [effect/target]}]
    (and target
         (entity/stat @target :stats/hp)))

  (component/do! [_ {:keys [effect/source effect/target]}]
    (let [source* @source
          target* @target
          hp (entity/stat target* :stats/hp)]
      (cond
       (zero? (hp 0))
       []

       (armor-saves? source* target*)
       [[:tx/add-text-effect target "[WHITE]ARMOR"]] ; TODO !_!_!_!_!_!

       :else
       (let [;_ (println "Source unmodified damage:" damage)
             {:keys [damage/min-max]} (->effective-damage damage source*)
             ;_ (println "\nSource modified: min-max:" min-max)
             min-max (entity/->modified-value target* :modifier/damage-receive min-max)
             ;_ (println "effective min-max: " min-max)
             dmg-amount (random/rand-int-between min-max)
             ;_ (println "dmg-amount: " dmg-amount)
             new-hp-val (max (- (hp 0) dmg-amount) 0)]
         [[:tx/audiovisual (:position target*) :audiovisuals/damage]
          [:tx/add-text-effect target (str "[RED]" dmg-amount)]
          [:tx/assoc-in target [:entity/stats :stats/hp 0] new-hp-val]
          [:tx/event target (if (zero? new-hp-val) :kill :alert)]])))))
