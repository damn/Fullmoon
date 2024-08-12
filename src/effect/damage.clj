(ns effect.damage
  (:require [core.component :refer [defcomponent]]
            [core.data :as data]
            [data.val-max :refer [apply-val apply-val-max-modifiers]]
            [utils.random :as random]
            [api.effect :as effect]
            [api.entity :as entity]))

; TODO move those fns to stats/armor or stats/damage namespace.

(defn- effective-armor-save [source* target*]
  (max (- (entity/armor-save target*)
          (entity/armor-pierce source*))
       0))

(comment
 (let [source* {:entity/stats {:stats/armor-pierce 0.4}}
       target* {:entity/stats {:stats/armor-save   0.5}}]
   (effective-armor-save source* target*))
 )

(defn- armor-saves? [source* target*]
  (< (rand) (effective-armor-save source* target*)))

(defn- apply-damage-modifiers [{:keys [damage/min-max] :as damage}
                               modifiers]
  (if modifiers
    (update damage :damage/min-max apply-val-max-modifiers modifiers)
    damage))

(comment
 (= (apply-damage-modifiers {:damage/min-max [5 10]}
                            {[:val :inc] [3]})
    #:damage{:min-max [8 10]})
 )

(defn- damage-deal-modifiers    [entity*] (-> entity* :entity/stats :stats/modifiers :stats.damage/deal))
(defn- damage-receive-modifiers [entity*] (-> entity* :entity/stats :stats/modifiers :stats.damage/receive))

(defn- apply-damage-deal-modifiers [damage source*]
  (apply-damage-modifiers damage (damage-deal-modifiers source*)))

(defn- apply-damage-receive-modifiers [damage target*]
  (apply-damage-modifiers damage (damage-receive-modifiers target*)))

(comment
 (= (apply-damage-deal-modifiers {:damage/min-max [5 10]}
                                 {:entity/stats {:stats/modifiers {:stats.damage/deal {[:val :inc] [1]}}}})
    #:damage{:min-max [6 10]})

 (= (apply-damage-deal-modifiers {:damage/min-max [5 10]}
                                 {:entity/stats {:stats/modifiers {:stats.damage/deal {[:max :mult] [2]}}}})
    #:damage{:min-max [5 30]})
 )

(defn- effective-damage
  ([damage source*]
   ; apply to damage
   ; stats.damage/deal
   (-> damage
       (apply-damage-deal-modifiers source*)))
  ([damage source* target*]
   ; apply to damage
   ; stats.damage/deal & stats.damage/receive
   (-> damage
       (apply-damage-deal-modifiers source*)
       (apply-damage-receive-modifiers target*))))

(comment
 (= (apply-damage-modifiers {:damage/min-max [3 10]}
                            {[:max :mult] 2
                             [:val :mult] 1.5
                             [:val :inc] 1
                             [:max :inc] 0})
    #:damage{:min-max [6 20]})

 (= (apply-damage-modifiers {:damage/min-max [6 20]}
                            {[:max :mult] 1
                             [:val :mult] 1
                             [:val :inc] -5
                             [:max :inc] 0})
    #:damage{:min-max [1 20]})

 (= (effective-damage {:damage/min-max [3 10]}
                      {:entity/stats {:stats/damage {:damage/deal {[:max :mult] 2
                                                                   [:val :mult] 1.5
                                                                   [:val :inc] 1
                                                                   [:max :inc] 0}}}}
                      {:entity/stats {:stats/damage {:damage/receive {[:max :mult] 1
                                                                      [:val :mult] 1
                                                                      [:val :inc] -5
                                                                      [:max :inc] 0}}}})
    #:damage{:min-max [1 20]})
 )

(defn- no-hp-left? [hp]
  (zero? (hp 0)))

(defn- damage->text [{[min-dmg max-dmg] :damage/min-max}]
  (str min-dmg "-" max-dmg " damage"))

(defcomponent :damage/min-max data/val-max-attr)

(defcomponent :effect/damage (data/map-attribute :damage/min-max)
  (effect/text [[_ damage] {:keys [effect/source]}]
    (if source
      (let [modified (effective-damage damage @source)]
        (if (= damage modified)
          (damage->text damage)
          (str (damage->text damage) "\nModified: " (damage->text modified))))
      (damage->text damage))) ; property menu no source,modifiers

  (effect/valid-params? [_ {:keys [effect/source effect/target]}]
    (and source target))

  (effect/txs [[_ damage] {:keys [effect/source effect/target]}]
    (let [source* @source
          target* @target
          hp (entity/hp target*)]
      (cond
       (not hp)
       []

       (no-hp-left? hp)
       []

       (armor-saves? source* target*)
       [[:tx/add-text-effect target "[WHITE]ARMOR"]] ; TODO !_!_!_!_!_!

       :else
       (let [{:keys [damage/min-max]} (effective-damage damage source* target*)
             ;_ (println "Damage/min-max: " min-max)
             dmg-amount (random/rand-int-between min-max)
             ;_ (println "dmg-amount: " dmg-amount)
             hp (apply-val (:stats/hp (:entity/stats target*)) #(- % dmg-amount))]
         ;(println "new hp:" hp)
         [[:tx.entity/audiovisual (entity/position target*) :audiovisuals/damage]
          [:tx/add-text-effect target (str "[RED]" dmg-amount)]
          ; TODO this breaks, directly oveerwriting the entity/hp (which is using calculated new stats)
          ; with something new
          [:tx.entity/assoc-in target [:entity/stats :stats/hp] hp]
          [:tx/event target (if (no-hp-left? hp) :kill :alert)]])))))

; TODO can I write a test for this ????
; should be possible ....
; could also deref source/target at effect-ctx applications .....
; like in entity ticks or so
