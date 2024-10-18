(ns world.effect.damage
  (:require [component.core :refer [defc]]
            [component.info :as info]
            [component.tx :as tx]
            [gdx.rand :refer [rand-int-between]]
            [world.entity.modifiers :refer [->modified-value]]
            [world.entity.stats :refer [entity-stat]]
            [world.effect :as effect :refer [source target]]))

(defn- entity->melee-damage [entity]
  (let [strength (or (entity-stat entity :stats/strength) 0)]
    {:damage/min-max [strength strength]}))

(defn- damage-effect []
  [:effect.entity/damage (entity->melee-damage @source)])

(defc :effect.entity/melee-damage
  {:schema :some}
  (info/text [_]
    (str "Damage based on entity strength."
         (when source
           (str "\n" (info/text (damage-effect))))))

  (effect/applicable? [_]
    (effect/applicable? (damage-effect)))

  (tx/do! [_]
    [(damage-effect)]))

(defn- effective-armor-save [source* target*]
  (max (- (or (entity-stat target* :stats/armor-save) 0)
          (or (entity-stat source* :stats/armor-pierce) 0))
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
  (update damage :damage/min-max #(->modified-value source* :modifier/damage-deal %)))

(comment
 (let [->source (fn [mods] {:entity/modifiers mods})]
   (and
    (= (->effective-damage {:damage/min-max [5 10]}
                           (->source {:modifier/damage-deal {:op/val-inc [1 5 10]
                                                            :op/val-mult [0.2 0.3]
                                                            :op/max-mult [1]}}))
       {:damage/min-max [31 62]})

    (= (->effective-damage {:damage/min-max [5 10]}
                           (->source {:modifier/damage-deal {:op/val-inc [1]}}))
       {:damage/min-max [6 10]})

    (= (->effective-damage {:damage/min-max [5 10]}
                           (->source {:modifier/damage-deal {:op/max-mult [2]}}))
       {:damage/min-max [5 30]})

    (= (->effective-damage {:damage/min-max [5 10]}
                           (->source nil))
       {:damage/min-max [5 10]}))))

(defn- damage->text [{[min-dmg max-dmg] :damage/min-max}]
  (str min-dmg "-" max-dmg " damage"))

(defc :damage/min-max {:schema :s/val-max})

(defc :effect.entity/damage
  {:let damage
   :schema [:s/map [:damage/min-max]]}
  (info/text [_]
    (if source
      (let [modified (->effective-damage damage @source)]
        (if (= damage modified)
          (damage->text damage)
          (str (damage->text damage) "\nModified: " (damage->text modified))))
      (damage->text damage))) ; property menu no source,modifiers

  (effect/applicable? [_]
    (and target
         (entity-stat @target :stats/hp)))

  (tx/do! [_]
    (let [source* @source
          target* @target
          hp (entity-stat target* :stats/hp)]
      (cond
       (zero? (hp 0))
       []

       (armor-saves? source* target*)
       [[:tx/add-text-effect target "[WHITE]ARMOR"]] ; TODO !_!_!_!_!_!

       :else
       (let [;_ (println "Source unmodified damage:" damage)
             {:keys [damage/min-max]} (->effective-damage damage source*)
             ;_ (println "\nSource modified: min-max:" min-max)
             min-max (->modified-value target* :modifier/damage-receive min-max)
             ;_ (println "effective min-max: " min-max)
             dmg-amount (rand-int-between min-max)
             ;_ (println "dmg-amount: " dmg-amount)
             new-hp-val (max (- (hp 0) dmg-amount) 0)]
         [[:tx/audiovisual (:position target*) :audiovisuals/damage]
          [:tx/add-text-effect target (str "[RED]" dmg-amount)]
          [:e/assoc-in target [:entity/stats :stats/hp 0] new-hp-val]
          [:tx/event target (if (zero? new-hp-val) :kill :alert)]])))))
