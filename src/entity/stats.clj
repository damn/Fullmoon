(ns entity.stats
  (:require [clojure.string :as str]
            [utils.random :as random]
            [data.val-max :refer [val-max-ratio lower-than-max? set-to-max apply-val-max-modifiers]]
            [core.component :refer [defcomponent]]
            [core.data :as data]
            api.context
            [api.effect :as effect]
            [api.entity :as entity]
            [api.graphics :as g]
            [gdx.graphics.color :as color]
            [api.tx :refer [transact!]]
            [context.ui.config :refer (hpbar-height-px)]))

; TODO
; * modifier texts
; * effect/target-entity - valid-params? broken
; * properties.item - :item/modifier no schema
; * default values
; * bounds (action-speed not <=0 , not value '-1' e.g.)/schema/values/allowed operations
; * editable in editor ? or first keep @ properties.edn ?
; * stats/modifiers in editor / for example max dmg reduced by 5 at stone golem
; * :inc :mult -> use namespaced keyword
; * we only work on max so could just use inc/mult @ val-max-modifiers hp/mana
; * take max-movement-speed into account @ :stats/movement-speed

(extend-type api.context.Context
  api.context/Modifier
  (modifier-text [_ modifier] ; only called at properties.item
    (binding [*print-level* nil]
      (with-out-str (clojure.pprint/pprint modifier)))
    #_(->> (for [component modifier] (modifier/text component))
         (str/join "\n"))))

(defn- update-modifiers [entity [stat operation] f]
  [:tx.entity/update-in entity [:entity/stats :stats/modifiers stat operation] f])

(defmethod transact! :tx/apply-modifier [[_ entity modifier] ctx]
  (for [{value 2 :as component} modifier]
    (update-modifiers entity component #(conj % value))))

(defmethod transact! :tx/reverse-modifier [[_ entity modifier] ctx]
  (for [{value 2 :as component} modifier]
    (update-modifiers entity component (fn [values]
                                         {:post [(= (count %) (dec (count values)))]}
                                         (remove #{value} values)))))

(defmulti ->effective-value (fn [stat _stats] stat))

(defmethod ->effective-value :stat/plain-number [stat stats]
  (let [modifiers (stat (:stats/modifiers stats))
        inc-modifiers (reduce + (:inc modifiers))
        mult-modifiers (reduce + 1 (:mult modifiers))]
    (-> (stat stats)
        (+ inc-modifiers)
        (* mult-modifiers))))

(defmethod ->effective-value :stat/only-inc [stat stats]
  (let [modifiers (stat (:stats/modifiers stats))
        inc-modifiers (reduce + (:inc modifiers))]
    (-> (stat stats)
        (+ inc-modifiers))))

(defmethod ->effective-value :stat/val-max [stat stats]
  (let [base-value (stat stats)
        modifiers (stat (:stats/modifiers stats))]
    (data.val-max/apply-val-max-modifiers base-value modifiers)))

(defcomponent :stats/hp data/pos-int-attr)
(derive :stats/hp :stat/val-max)

(defcomponent :stats/mana data/nat-int-attr)
(derive :stats/mana :stat/val-max)

; hp & mana have [:max :inc] and [:max :mult] operations
; only going on max so can just make :inc and :mult

; so defining modifiers [:stats/hp :inc] [:stats/hp :mult] , same for mana
; based on the data ... ? (val-max)
; manual ?

(defcomponent :stats/movement-speed data/pos-attr)
(derive :stats/movement-speed :stat/plain-number)

(defcomponent :stats/strength data/nat-int-attr)
(derive :stats/strength :stat/only-inc)

(let [doc "action-time divided by this stat when a skill is being used.
          Default value 1.

          For example:
          attack/cast-speed 1.5 => (/ action-time 1.5) => 150% attackspeed."
      skill-speed-stat (assoc data/pos-attr :doc doc)]
  (defcomponent :stats/cast-speed   skill-speed-stat)
  (defcomponent :stats/attack-speed skill-speed-stat))
(derive :stats/cast-speed :stat/only-inc)
(derive :stats/attack-speed :stat/only-inc)

(defcomponent :stats/armor-save {:widget :text-field :schema number?})
(derive :stats/armor-save :stat/only-inc)

(defcomponent :stats/armor-pierce {:widget :text-field :schema number?})
(derive :stats/armor-pierce :stat/only-inc)

(defcomponent :stats/damage-deal {})
(defcomponent :stats/damage-receive {})

(extend-type api.entity.Entity
  entity/Stats
  (stat [{:keys [entity/stats]} stat-k]
    (when (stat-k stats)
      (->effective-value stat-k stats))))

(def ^:private hpbar-colors
  {:green     [0 0.8 0]
   :darkgreen [0 0.5 0]
   :yellow    [0.5 0.5 0]
   :red       [0.5 0 0]})

(defn- hpbar-color [ratio]
  (let [ratio (float ratio)
        color (cond
                (> ratio 0.75) :green
                (> ratio 0.5)  :darkgreen
                (> ratio 0.25) :yellow
                :else          :red)]
    (color hpbar-colors)))

(def ^:private borders-px 1)

(def ^:private stats-keywords
  [:stats/hp
   :stats/mana
   :stats/movement-speed
   :stats/strength
   :stats/cast-speed
   :stats/attack-speed
   :stats/armor-save
   :stats/armor-pierce])

(defcomponent :entity/stats (data/components-attribute :stats)
  (entity/create-component [[_ stats] _components _ctx]
    (-> stats
        (update :stats/hp (fn [hp] [hp hp]))
        (update :stats/mana (fn [mana] [mana mana])) ))

  ; TODO proper texts...
  ; HP color based on ratio like hp bar samey
  ; mana color same in the whole app
  (entity/info-text [[_ {:keys [stats/modifiers] :as stats}] _ctx]
    (str (str/join "\n"
                   (for [k stats-keywords
                         :let [stat (k stats)]
                         :when stat]
                     (str (name k) ": " (->effective-value k stats))))
         (when modifiers
           (str "\n[LIME] Modifiers:\n"
                (binding [*print-level* nil]
                  (with-out-str
                   (clojure.pprint/pprint (sort-by key modifiers))))))))

  (entity/render-info [_
                       {{:keys [width half-width half-height]} :entity/body
                        :keys [entity/mouseover?] :as entity*}
                       g
                       _ctx]
    (let [ratio (val-max-ratio (entity/stat entity* :stats/hp))
          [x y] (entity/position entity*)]
      (when (or (< ratio 1) mouseover?)
        (let [x (- x half-width)
              y (+ y half-height)
              height (g/pixels->world-units g hpbar-height-px)
              border (g/pixels->world-units g borders-px)]
          (g/draw-filled-rectangle g x y width height color/black)
          (g/draw-filled-rectangle g
                                   (+ x border)
                                   (+ y border)
                                   (- (* width ratio) (* 2 border))
                                   (- height (* 2 border))
                                   (hpbar-color ratio)))))))

(defmethod transact! :tx.entity.stats/pay-mana-cost [[_ entity cost] _ctx]
  (let [mana-val ((entity/stat @entity :stats/mana) 0)]
    (assert (<= cost mana-val))
    [[:tx.entity/assoc-in entity [:entity/stats :stats/mana 0] (- mana-val cost)]]))

(comment
 (let [mana-val 4
       entity (atom (entity/map->Entity {:entity/stats {:stats/mana [mana-val 10]}}))
       mana-cost 3
       resulting-mana (- mana-val mana-cost)]
   (= (transact! [:tx.entity.stats/pay-mana-cost entity mana-cost] nil)
      [[:tx.entity/assoc-in entity [:entity/stats :stats/mana 0] resulting-mana]]))
 )

(defmacro def-set-to-max-effect [stat]
  `(let [component# ~(keyword "effect" (str (name (namespace stat)) "-" (name stat) "-set-to-max"))]
     (defcomponent component# {:widget :label
                               :schema [:= true]
                               :default-value true}
       (effect/text ~'[_ _effect-ctx]
         ~(str "Sets " (name stat) " to max."))

       (effect/valid-params? ~'[_ {:keys [effect/source]}]
         ~'source)

       (effect/useful? ~'[_ {:keys [effect/source]} _ctx]
         (lower-than-max? (~stat (:entity/stats @~'source))))

       (transact! ~'[_ {:keys [effect/source]}]
         [[:tx.entity/assoc-in ~'source [:entity/stats ~stat] (set-to-max (~stat (:entity/stats @~'source)))]]))))

(def-set-to-max-effect :stats/hp)
(def-set-to-max-effect :stats/mana)

#_(defcomponent :effect/set-to-max {:widget :label
                                    :schema [:= true]
                                    :default-value true}
  (effect/text [[_ stat] _effect-ctx]
    (str "Sets " (name stat) " to max."))

  (effect/valid-params? [_ {:keys [effect/source]}]
    source)

  (effect/useful? [[_ stat] {:keys [effect/source]} _ctx]
    (lower-than-max? (stat @source)))

  (effect/txs [[_ stat] {:keys [effect/source]}]
    [[:tx.entity/assoc source stat (set-to-max (stat @source))]]))

; this as macro ... ? component which sets the value of another component ??
#_(defcomponent :effect/set-mana-to-max {:widget :label
                                         :schema [:= true]
                                         :default-value true}
  (effect/valid-params? [_ {:keys [effect/source]}] source)
  (effect/text    [_ _effect-ctx]     (effect/text    [:effect/set-to-max :entity/mana]))
  (effect/useful? [_ effect-ctx _ctx] (effect/useful? [:effect/set-to-max :entity/mana] effect-ctx))
  (effect/txs     [_ effect-ctx]      (effect/txs     [:effect/set-to-max :entity/mana] effect-ctx)))

#_[:effect/set-to-max :entity/hp]
#_[:effect/set-to-max :entity/mana]

(defn- entity*->melee-damage [entity*]
  (let [strength (or (entity/stat entity* :stats/strength) 0)]
    {:damage/min-max [strength strength]}))

(defn- damage-effect [{:keys [effect/source]}]
  [:effect/damage (entity*->melee-damage @source)])

(defcomponent :effect/melee-damage {}
  (effect/text [_ {:keys [effect/source] :as effect-ctx}]
    (str "Damage based on entity strength."
         (when source
           (str "\n" (effect/text (damage-effect effect-ctx)
                                  effect-ctx)))))

  (effect/valid-params? [_ effect-ctx]
    (effect/valid-params? (damage-effect effect-ctx)))

  (transact! [_ ctx]
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

(defn- dmg-apply-modifiers [{:keys [damage/min-max] :as damage} modifiers]
  (update damage :damage/min-max apply-val-max-modifiers modifiers))

(comment
 (= (dmg-apply-modifiers {:damage/min-max [5 10]}
                         {[:val :inc] [3]})
    #:damage{:min-max [8 10]})

 (= (dmg-apply-modifiers {:damage/min-max [5 10]}
                         nil)
    #:damage{:min-max [5 10]})

 )

(defn- modifiers [entity* stat]
  (-> entity* :entity/stats :stats/modifiers stat))

(defn- apply-damage-modifiers [damage entity* stat]
  (dmg-apply-modifiers damage (modifiers entity* stat)))

(comment
 (= (apply-damage-modifiers {:damage/min-max [5 10]}
                            {:entity/stats {:stats/modifiers {:stats/damage-deal {[:val :inc] [1]}}}}
                            :stats/damage-deal)
    #:damage{:min-max [6 10]})

 (= (apply-damage-modifiers {:damage/min-max [5 10]}
                            {:entity/stats {:stats/modifiers {:stats/damage-deal {[:max :mult] [2]}}}}
                            :stats/damage-deal)
    #:damage{:min-max [5 30]})

 (= (apply-damage-modifiers {:damage/min-max [5 10]}
                            {:entity/stats {:stats/modifiers nil}}
                            :stats/damage-receive)
    #:damage{:min-max [5 10]})
 )

(defn- effective-damage
  ([damage source*]
   (apply-damage-modifiers damage source* :stats/damage-deal))

  ([damage source* target*]
   (-> (effective-damage damage source*)
       (apply-damage-modifiers target* :stats/damage-receive))))

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

  (transact! [[_ damage] {:keys [effect/source effect/target]}]
    (let [source* @source
          target* @target
          hp (entity/stat target* :stats/hp)]
      (cond
       (or (not hp) (zero? (hp 0)))
       []

       (armor-saves? source* target*)
       [[:tx/add-text-effect target "[WHITE]ARMOR"]] ; TODO !_!_!_!_!_!

       :else
       (let [{:keys [damage/min-max]} (effective-damage damage source* target*)
             dmg-amount (random/rand-int-between min-max)
             new-hp-val (max (- (hp 0) dmg-amount) 0)]
         [[:tx.entity/audiovisual (entity/position target*) :audiovisuals/damage]
          [:tx/add-text-effect target (str "[RED]" dmg-amount)]
          [:tx.entity/assoc-in target [:entity/stats :stats/hp 0] new-hp-val]
          [:tx/event target (if (zero? new-hp-val) :kill :alert)]])))))
