(ns cdq.tx.damage
  (:require [core.component :as component]
            [data.val-max :refer [apply-val apply-val-max-modifiers]]
            [utils.random :as random]
            [cdq.api.context :refer [transact!]]
            [cdq.api.effect :as effect]
            [cdq.attributes :as attr]))

(defn- effective-armor-save [source* target*]
  (max (- (or (-> target* :entity/stats :stats/armor-save)   0)
          (or (-> source* :entity/stats :stats/armor-pierce) 0))
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
                            {[:val :inc] 3})
    #:damage{:min-max [8 10]})
 )

(defn- damage-stats [entity*]
  (-> entity* :entity/stats :stats/damage))

(defn- apply-source-modifiers [damage source*]
  (apply-damage-modifiers damage (-> source* damage-stats :damage/deal)))

(defn- apply-target-modifiers [damage target*]
  (apply-damage-modifiers damage (-> target* damage-stats :damage/receive)))

(comment
 (= (apply-source-modifiers {:damage/min-max [5 10]}
                            {:entity/stats {:stats/damage {:damage/deal {[:val :inc] 1}}}})
    #:damage{:min-max [6 10]})

 (= (apply-source-modifiers {:damage/min-max [5 10]}
                            {:entity/stats {:stats/damage {:damage/deal {[:val :inc] 1}}}})
    #:damage{:min-max [6 10]})

 (= (apply-source-modifiers {:damage/min-max [5 10]}
                            {:entity/stats {:stats/damage {:damage/deal {[:max :mult] 3}}}})
    #:damage{:min-max [5 30]})
 )

(defn- effective-damage
  ([damage source*]
   (-> damage
       (apply-source-modifiers source*)))
  ([damage source* target*]
   (-> damage
       (apply-source-modifiers source*)
       (apply-target-modifiers target*))))

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

(component/def :damage/min-max attr/val-max-attr)

(component/def :tx/damage (attr/map-attribute :damage/min-max)
  damage
  (effect/text [_ {:keys [effect/source]}]
    (if source
      (let [modified (effective-damage damage @source)]
        (if (= damage modified)
          (damage->text damage)
          (str (damage->text damage) "\nModified: " (damage->text modified))))
      (damage->text damage))) ; property menu no source,modifiers

  (effect/valid-params? [_ {:keys [effect/source effect/target]}]
    (and source target))

  (transact! [_ {:keys [effect/source effect/target]}]
    (let [source* @source
          {:keys [entity/position entity/hp] :as target*} @target]
      (cond
       (not hp)
       []

       (no-hp-left? hp)
       []

       (armor-saves? source* target*)
       [[:tx/add-text-effect target "[WHITE]ARMOR"]] ; TODO !_!_!_!_!_!

       :else
       (let [{:keys [damage/min-max]} (effective-damage damage source* target*)
             dmg-amount (random/rand-int-between min-max)
             hp (apply-val hp #(- % dmg-amount))]
         [[:tx/audiovisual position :effects.damage/hit-effect]
          [:tx/add-text-effect target (str "[RED]" dmg-amount)]
          [:tx/assoc target :entity/hp hp]
          [:tx/event target (if (no-hp-left? hp) :kill :alert)]])))))
