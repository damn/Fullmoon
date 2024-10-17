(ns core.effect.entity
  (:require [gdx.graphics :as g]
            [gdx.rand :refer [rand-int-between]]
            [component.core :refer [defc]]
            [component.info :as info]
            [component.tx :as tx]
            [core.effect :as effect :refer [source target]]
            [utils.core :refer [readable-number]]
            [world.core :refer [timer stopped? finished-ratio]]
            [world.entity :as entity]
            [world.entity.faction :as faction]
            [world.entity.modifiers :refer [->modified-value]]
            [world.entity.stats :refer [entity-stat]]))

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

(defc :damage/min-max {:schema :val-max})

(defc :effect.entity/damage
  {:let damage
   :schema [:map [:damage/min-max]]}
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
            (faction/enemy @source))))

  (tx/do! [_]
    [[:tx/audiovisual (:position @target) :audiovisuals/convert]
     [:e/assoc target :entity/faction (faction/friend @source)]]))

(defc :effect.entity/stun
  {:schema :pos
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
