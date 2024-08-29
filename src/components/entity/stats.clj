(ns components.entity.stats
  (:require [clojure.string :as str]
            [malli.core :as m]
            [gdx.graphics.color :as color]
            [utils.core :as utils :refer [k->pretty-name readable-number]]
            [utils.random :as random]
            [core.val-max :refer [val-max-ratio]]
            [core.component :as component :refer [defcomponent defcomponent*]]
            [core.entity :as entity]
            [core.graphics :as g]
            [core.operation :as operation]
            [core.modifiers :as modifiers]))

(defn- conj-value [value]
  (fn [values]
    (conj values value)))

(defn- remove-value [value]
  (fn [values]
    {:post [(= (count %) (dec (count values)))]}
    (utils/remove-one values value)))

(defn- txs-update-modifiers [entity modifiers f]
  (for [[modifier-k operations] modifiers
        [operation-k value] operations]
    [:tx.entity/update-in entity [:entity/stats :stats/modifiers modifier-k operation-k] (f value)]))

(comment
 (= (txs-update-modifiers :entity
                         {:modifier/hp {:op/max-inc 5
                                        :op/max-mult 0.3}
                          :modifier/movement-speed {:op/mult 0.1}}
                         (fn [value] :fn))
    [[:tx.entity/update-in :entity [:entity/stats :stats/modifiers :modifier/hp :op/max-inc] :fn]
     [:tx.entity/update-in :entity [:entity/stats :stats/modifiers :modifier/hp :op/max-mult] :fn]
     [:tx.entity/update-in :entity [:entity/stats :stats/modifiers :modifier/movement-speed :op/mult] :fn]])
 )

(defcomponent :tx/apply-modifiers
  (component/do! [[_ entity modifiers] _ctx]
    (txs-update-modifiers entity modifiers conj-value)))

(defcomponent :tx/reverse-modifiers
  (component/do! [[_ entity modifiers] _ctx]
    (txs-update-modifiers entity modifiers remove-value)))

; DRY ->effective-value (summing)
; also: sort-by op/order @ modifier/info-text itself (so player will see applied order)
(defn- sum-operation-values [stats-modifiers]
  (for [[modifier-k operations] stats-modifiers
        :let [operations (for [[operation-k values] operations
                               :let [value (apply + values)]
                               :when (not (zero? value))]
                           [operation-k value])]
        :when (seq operations)]
    [modifier-k operations]))

(defn- stats-modifiers-info-text [stats-modifiers]
  (let [modifiers (sum-operation-values stats-modifiers)]
    (when (seq modifiers)
      (modifiers/info-text modifiers))))

(defn- ->effective-value [base-value modifier-k stats]
  {:pre [(= "modifier" (namespace modifier-k))]}
  (->> stats
       :stats/modifiers
       modifier-k
       (sort-by component/order)
       (reduce (fn [base-value [operation-k values]]
                 (component/apply [operation-k (apply + values)] base-value))
               base-value)))

(comment
 (and
  (= (->effective-value [5 10]
                        :modifier/damage-deal
                        {:stats/modifiers {:modifier/damage-deal {:op/val-inc [30]
                                                                  :op/val-mult [0.5]}}})
     [52 52])
  (= (->effective-value [5 10]
                        :modifier/damage-deal
                        {:stats/modifiers {:modifier/damage-deal {:op/val-inc [30]}
                                           :stats/fooz-barz {:op/babu [1 2 3]}}})
     [35 35])
  (= (->effective-value [5 10]
                        :modifier/damage-deal
                        {:stats/modifiers {}})
     [5 10])
  (= (->effective-value [100 100]
                        :modifier/hp
                        {:stats/modifiers {:modifier/hp {:op/max-inc [10 1]
                                                         :op/max-mult [0.5]}}})
     [100 166])
  (= (->effective-value 3
                        :modifier/movement-speed
                        {:stats/modifiers {:modifier/movement-speed {:op/inc [2]
                                                                     :op/mult [0.1 0.2]}}})
     6.5))
 )

(defn- defmodifier [k operations]
  (defcomponent* k {:data [:components operations]}))

(defn- stat-k->modifier-k [k]
  (keyword "modifier" (name k)))

(defn- defstat [k {:keys [operations] :as attr-m}]
  (defcomponent* k attr-m)
  (when operations
    (defmodifier (stat-k->modifier-k k) operations)))

; TODO modifiers/effects based on data? don't have to use to change aggro-range etc?
; => but then stats/hp has different schema than its actually used?
; modifiers and effects use directly stat ??
; also create fns component/create move here ? (e.g. hp)

(defstat :stats/reaction-time
  {:data :pos-int
   :optional? false})

(defstat :stats/hp
  {:data :pos-int
   :optional? false
   :operations [:op/max-inc :op/max-mult]})

(defstat :stats/mana
  {:data :nat-int
   :optional? true
   :operations [:op/max-inc :op/max-mult]})

(defn- effect-k->stat-k [effect-k]
  (keyword "stats" (name effect-k)))

; TODO says here 'Minimum' hp instead of just 'HP'
; Sets to 0 but don't kills
; Could even set to a specific value ->
; op/set-to-ratio 0.5 ....
; sets the hp to 50%...

; is called :base/stat-effect so it doesn't show up in (data/namespace-components :effect) list in editor
; for :skill/effects
(defcomponent :base/stat-effect
  (component/info-text [[k operations] _effect-ctx]
    (str/join "\n"
              (for [operation operations]
                (str (operation/info-text operation) " " (k->pretty-name k)))))

  (component/applicable? [[k _] {:keys [effect/target]}]
    (and target
         (entity/stat @target (effect-k->stat-k k))))

  (component/useful? [_ _effect-ctx]
    true)

  (component/do! [[effect-k operations] {:keys [effect/target]}]
    (let [stat-k (effect-k->stat-k effect-k)]
      (when-let [effective-value (entity/stat @target stat-k)]
        [[:tx.entity/assoc-in target [:entity/stats stat-k]
          ; TODO similar to ->effective-value
          ; but operations not sort-by component/order ??
          ; component/apply reuse fn over operations to get effectiv value
          (reduce (fn [value operation] (component/apply operation value))
                  effective-value
                  operations)]]))))

(defcomponent :effect.entity/hp
  {:data [:components [:op/val-inc :op/val-mult :op/max-inc :op/max-mult]]})
(derive :effect.entity/hp :base/stat-effect)

(defcomponent :effect.entity/mana
  {:data [:components [:op/val-inc :op/val-mult :op/max-inc :op/max-mult]]})
(derive :effect.entity/mana :base/stat-effect)

; * TODO clamp/post-process effective-values @ stat-k->effective-value
; * just don't create movement-speed increases too much?
; * dont remove strength <0 or floating point modifiers  (op/int-inc ?)
; * cast/attack speed dont decrease below 0 ??

; TODO clamp between 0 and max-speed ( same as movement-speed-schema )
(defstat :stats/movement-speed
  {:data :pos;(m/form entity/movement-speed-schema)
   :optional? false
   :operations [:op/inc :op/mult]})

; TODO show the stat in different color red/green if it was permanently modified ?
; or an icon even on the creature
; also we want audiovisuals always ...
(defcomponent :effect.entity/movement-speed
  {:data [:components [:op/mult]]})
(derive :effect.entity/movement-speed :base/stat-effect)

; TODO clamp into ->pos-int
(defstat :stats/strength
  {:data :nat-int
   :optional? false
   :operations [:op/inc]})

; TODO here >0
(let [doc "action-time divided by this stat when a skill is being used.
          Default value 1.

          For example:
          attack/cast-speed 1.5 => (/ action-time 1.5) => 150% attackspeed."
      data :pos
      operations [:op/inc]
      optional? true
      ]
  (defstat :stats/cast-speed
    {:data data
     :doc doc
     :optional? optional?
     :operations operations})

  (defstat :stats/attack-speed
    {:data data
     :doc doc
     :optional? optional?
     :operations operations}))

; TODO bounds
(defstat :stats/armor-save
  {:data :number
   :optional? true
   :operations [:op/inc]})

(defstat :stats/armor-pierce
  {:data :number
   :optional? true
   :operations [:op/inc]})

; TODO needs to be there for each npc - make non-removable (added to all creatures)
(defstat :stats/aggro-range
  {:data :nat-int
   :optional? false})

; TODO negate this value also @ use
; so can make positiive modifeirs green , negative red....
(defstat :stats/damage-receive
  {:data :number
   :optional? true
   :operations [:op/inc :op/mult]})

; TODO kommt aufs gleiche raus if we have +1 min damage or +1 max damage?
; just inc/mult ?
; or even mana/hp does it make a difference ?
(defmodifier :modifier/damage-deal [:op/val-inc :op/val-mult :op/max-inc :op/max-mult])

#_(defcomponent :stats/modifiers {:data [:components [:modifier/damage-deal :modifier/damage-receive]]})

(defn- stat-k->effective-value [stat-k stats]
  (when-let [base-value (stat-k stats)]
    (->effective-value base-value (stat-k->modifier-k stat-k) stats)))

(extend-type core.entity.Entity
  entity/Stats
  (stat [{:keys [entity/stats]} stat-k]
    (stat-k->effective-value stat-k stats)))

; TODO remove vector shaboink -> gdx.graphics.color/->color use.
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

(defn- build-modifiers [modifiers]
  (into {} (for [[modifier-k operations] modifiers]
             [modifier-k (into {} (for [[operation-k value] operations]
                                    [operation-k [value]]))])))

(comment
 (= {:modifier/damage-receive {:op/mult [-0.9]}}
    (build-modifiers {:modifier/damage-receive {:op/mult -0.9}}))
 )

(let [stats-order [:stats/hp
                   :stats/mana
                   ;:stats/movement-speed
                   :stats/strength
                   :stats/cast-speed
                   :stats/attack-speed
                   :stats/armor-save
                   :stats/damage-receive
                   ;:stats/armor-pierce
                   ;:stats/aggro-range
                   ;:stats/reaction-time
                   ;:stats/modifiers
                   ]]
  (defn- stats-info-texts [stats]
    (str/join "\n"
              (for [stat-k stats-order
                    :let [value (stat-k->effective-value stat-k stats)]
                    :when value]
                (str (k->pretty-name stat-k) ": " value)))))

(defcomponent :entity/stats
  {:data [:components-ns :stats]
   :optional? false
   :let stats}
  (component/create [_ _ctx]
    (-> stats
        (update :stats/hp (fn [hp] (when hp [hp hp])))
        (update :stats/mana (fn [mana] (when mana [mana mana])))
        (update :stats/modifiers build-modifiers)))

  (component/info-text [_ _ctx]
    (str (stats-info-texts (dissoc stats :stats/modifiers))
         "\n"
         (stats-modifiers-info-text (:stats/modifiers stats))))

  (component/render-info [_ entity* g _ctx]
    (when-let [hp (entity/stat entity* :stats/hp)]
      (let [ratio (val-max-ratio hp)
            {:keys [position width half-width half-height entity/mouseover?]} entity*
            [x y] position]
        (when (or (< ratio 1) mouseover?)
          (let [x (- x half-width)
                y (+ y half-height)
                height (g/pixels->world-units g entity/hpbar-height-px)
                border (g/pixels->world-units g borders-px)]
            (g/draw-filled-rectangle g x y width height color/black)
            (g/draw-filled-rectangle g
                                     (+ x border)
                                     (+ y border)
                                     (- (* width ratio) (* 2 border))
                                     (- height (* 2 border))
                                     (hpbar-color ratio))))))))

(defcomponent :tx.entity.stats/pay-mana-cost
  (component/do! [[_ entity cost] _ctx]
    (let [mana-val ((entity/stat @entity :stats/mana) 0)]
      (assert (<= cost mana-val))
      [[:tx.entity/assoc-in entity [:entity/stats :stats/mana 0] (- mana-val cost)]])))

(comment
 (let [mana-val 4
       entity (atom (entity/map->Entity {:entity/stats {:stats/mana [mana-val 10]}}))
       mana-cost 3
       resulting-mana (- mana-val mana-cost)]
   (= (component/do! [:tx.entity.stats/pay-mana-cost entity mana-cost] nil)
      [[:tx.entity/assoc-in entity [:entity/stats :stats/mana 0] resulting-mana]]))
 )

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
  (update damage :damage/min-max ->effective-value :modifier/damage-deal (:entity/stats source*)))

(comment
 (let [->stats (fn [mods] {:entity/stats {:stats/modifiers mods}})]
   (and
    (= (->effective-damage {:damage/min-max [5 10]}
                           (->stats {:modifier/damage-deal {:op/val-inc [1 5 10]
                                                            :op/val-mult [0.2 0.3]
                                                            :op/max-mult [1]}}))
       #:damage{:min-max [31 62]})

    (= (->effective-damage {:damage/min-max [5 10]}
                           (->stats {:modifier/damage-deal {:op/val-inc [1]}}))
       #:damage{:min-max [6 10]})

    (= (->effective-damage {:damage/min-max [5 10]}
                           (->stats {:modifier/damage-deal {:op/max-mult [2]}}))
       #:damage{:min-max [5 30]})

    (= (->effective-damage {:damage/min-max [5 10]}
                           (->stats nil))
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
       (let [{:keys [damage/min-max]} (->effective-damage damage source*)
             ;_ (println "\nmin-max:" min-max)
             dmg-amount (random/rand-int-between min-max)
             ;_ (println "dmg-amount: " dmg-amount)
             dmg-amount (->effective-value dmg-amount :modifier/damage-receive (:entity/stats target*))
             ;_ (println "effective dmg-amount: " dmg-amount)
             new-hp-val (max (- (hp 0) dmg-amount) 0)]
         [[:tx.entity/audiovisual (:position target*) :audiovisuals/damage]
          [:tx/add-text-effect target (str "[RED]" dmg-amount)]
          [:tx.entity/assoc-in target [:entity/stats :stats/hp 0] new-hp-val]
          [:tx/event target (if (zero? new-hp-val) :kill :alert)]])))))
