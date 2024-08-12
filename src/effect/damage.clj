(ns effect.damage
  (:require [core.component :refer [defcomponent]]
            [core.data :as data]
            [data.val-max :refer [apply-val apply-val-max-modifiers]]
            [utils.random :as random]
            [api.effect :as effect]
            [api.entity :as entity]))

; breaks for projectile - doesn't have armor-pierce
; should be part of the hit-effect ...
(defn- effective-armor-save [source* target*]
  (println "effective-armor-save " (:entity/uid source*) "," (:entity/uid target*))
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

(defn- apply-modifiers [{:keys [damage/min-max] :as damage}
                        modifiers]
  (update damage :damage/min-max apply-val-max-modifiers modifiers))

(comment
 (= (apply-modifiers {:damage/min-max [5 10]}
                     {[:val :inc] [3]})
    #:damage{:min-max [8 10]})

 (= (apply-modifiers {:damage/min-max [5 10]}
                     nil)
    #:damage{:min-max [5 10]})

 )

(defn- modifiers [entity* stat]
  (-> entity* :entity/stats :stats/modifiers stat))

(defn- apply-damage-modifiers [damage entity* stat]
  (apply-modifiers damage (modifiers entity* stat)))

(comment
 (= (apply-damage-modifiers {:damage/min-max [5 10]}
                            {:entity/stats {:stats/modifiers {:stats.damage/deal {[:val :inc] [1]}}}}
                            :stats.damage/deal)
    #:damage{:min-max [6 10]})

 (= (apply-damage-modifiers {:damage/min-max [5 10]}
                            {:entity/stats {:stats/modifiers {:stats.damage/deal {[:max :mult] [2]}}}}
                            :stats.damage/deal)
    #:damage{:min-max [5 30]})

 (= (apply-damage-modifiers {:damage/min-max [5 10]}
                            {:entity/stats {:stats/modifiers nil}}
                            :stats.damage/receive)
    #:damage{:min-max [5 10]})
 )

(defn- effective-damage
  ([damage source*]
   (apply-damage-modifiers damage source* :stats.damage/deal))

  ([damage source* target*]
   (-> (effective-damage damage source*)
       (apply-damage-modifiers target* :stats.damage/receive))))

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
                      {:entity/stats {:stats/damage {:damage/deal {[:max :mult] [2]
                                                                   [:val :mult] [1.5]
                                                                   [:val :inc] [1]
                                                                   [:max :inc] [0]}}}}
                      {:entity/stats {:stats/damage {:damage/receive {[:max :mult] [1]
                                                                      [:val :mult] [1]
                                                                      [:val :inc] [-5]
                                                                      [:max :inc] [0]}}}})
    #:damage{:min-max [3 10]})
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
          hp (entity/stat target* :stats/hp)]
      (cond
       (not hp)
       []

       (no-hp-left? hp)
       []

       (armor-saves? source* target*)
       [[:tx/add-text-effect target "[WHITE]ARMOR"]] ; TODO !_!_!_!_!_!

       :else
       (let [{:keys [damage/min-max]} (effective-damage damage source* target*)
             dmg-amount (random/rand-int-between min-max)]
         [[:tx.entity/audiovisual (entity/position target*) :audiovisuals/damage]
          [:tx/add-text-effect target (str "[RED]" dmg-amount)]

          [:tx.entity/assoc-in target [:entity/stats :stats/hp 0] (- ((entity/stat target* :stats/hp) 0) dmg-amount)]
          ;[:tx.entity.stats/hp-val-inc target (- dmg-amount)]
          ; TODO this doesnt use apply-val , so it goes to [0 5 ] or even negative


          ; TODO this doesnt use latest hp ... !
          [:tx/event target (if (no-hp-left? hp) :kill :alert)]])))))

; we need a test for this fn .... ?
