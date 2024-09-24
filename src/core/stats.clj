(ns core.stats
  (:require [clojure.string :as str]
            [clojure.math :as math]
            #_[malli.core :as m]
            [core.ctx :refer :all]
            [core.entity :as entity])
  (:import com.badlogic.gdx.graphics.Color))

(defsystem op-value-text "FIXME" [_])
(defsystem op-apply "FIXME" [_ base-value])
(defsystem op-order "FIXME" [_])

(defn- +? [n]
  (case (math/signum n)
    0.0 ""
    1.0 "+"
    -1.0 ""))

(defn- op-info-text [{value 1 :as operation}]
  (str (+? value) (op-value-text operation)))

(defcomponent :op/inc
  {:data :number
   :let value}
  (op-value-text [_] (str value))
  (op-apply [_ base-value] (+ base-value value))
  (op-order [_] 0))

(defcomponent :op/mult
  {:data :number
   :let value}
  (op-value-text [_] (str (int (* 100 value)) "%"))
  (op-apply [_ base-value] (* base-value (inc value)))
  (op-order [_] 1))

(defn- ->pos-int [v]
  (-> v int (max 0)))

(defn- val-max-op-k->parts [op-k]
  (let [[val-or-max inc-or-mult] (mapv keyword (str/split (name op-k) #"-"))]
    [val-or-max (keyword "op" (name inc-or-mult))]))

(comment
 (= (val-max-op-k->parts :op/val-inc) [:val :op/inc])
 )

(defcomponent :op/val-max
  (op-value-text [[op-k value]]
    (let [[val-or-max op-k] (val-max-op-k->parts op-k)]
      (str (op-value-text [op-k value]) " " (case val-or-max
                                              :val "Minimum"
                                              :max "Maximum"))))


  (op-apply [[operation-k value] val-max]
    (assert (m/validate val-max-schema val-max) (pr-str val-max))
    (let [[val-or-max op-k] (val-max-op-k->parts operation-k)
          f #(op-apply [op-k value] %)
          [v mx] (update val-max (case val-or-max :val 0 :max 1) f)
          v  (->pos-int v)
          mx (->pos-int mx)
          vmx (case val-or-max
                :val [v (max v mx)]
                :max [(min v mx) mx])]
      (assert (m/validate val-max-schema vmx))
      vmx))

  (op-order [[op-k value]]
    (let [[_ op-k] (val-max-op-k->parts op-k)]
      (op-order [op-k value]))))

(defcomponent :op/val-inc {:data :int})
(derive       :op/val-inc :op/val-max)

(defcomponent :op/val-mult {:data :number})
(derive       :op/val-mult :op/val-max)

(defcomponent :op/max-inc {:data :int})
(derive       :op/max-inc :op/val-max)

(defcomponent :op/max-mult {:data :number})
(derive       :op/max-mult :op/val-max)

(comment
 (and
  (= (op-apply [:op/val-inc 30]    [5 10]) [35 35])
  (= (op-apply [:op/max-mult -0.5] [5 10]) [5 5])
  (= (op-apply [:op/val-mult 2]    [5 10]) [15 15])
  (= (op-apply [:op/val-mult 1.3]  [5 10]) [11 11])
  (= (op-apply [:op/max-mult -0.8] [5 10]) [1 1])
  (= (op-apply [:op/max-mult -0.9] [5 10]) [0 0]))
 )

(defn- txs-update-modifiers [entity modifiers f]
  (for [[modifier-k operations] modifiers
        [operation-k value] operations]
    [:e/update-in entity [:entity/modifiers modifier-k operation-k] (f value)]))

(defn- conj-value [value]
  (fn [values]
    (conj values value)))

(defn- remove-value [value]
  (fn [values]
    {:post [(= (count %) (dec (count values)))]}
    (remove-one values value)))

(comment
 (= (txs-update-modifiers :entity
                         {:modifier/hp {:op/max-inc 5
                                        :op/max-mult 0.3}
                          :modifier/movement-speed {:op/mult 0.1}}
                         (fn [_value] :fn))
    [[:e/update-in :entity [:entity/modifiers :modifier/hp :op/max-inc] :fn]
     [:e/update-in :entity [:entity/modifiers :modifier/hp :op/max-mult] :fn]
     [:e/update-in :entity [:entity/modifiers :modifier/movement-speed :op/mult] :fn]])
 )

(defcomponent :tx/apply-modifiers
  (do! [[_ entity modifiers] _ctx]
    (txs-update-modifiers entity modifiers conj-value)))

(defcomponent :tx/reverse-modifiers
  (do! [[_ entity modifiers] _ctx]
    (txs-update-modifiers entity modifiers remove-value)))

; DRY ->effective-value (summing)
; also: sort-by op/order @ modifier/info-text itself (so player will see applied order)
(defn- sum-operation-values [modifiers]
  (for [[modifier-k operations] modifiers
        :let [operations (for [[operation-k values] operations
                               :let [value (apply + values)]
                               :when (not (zero? value))]
                           [operation-k value])]
        :when (seq operations)]
    [modifier-k operations]))

(com.badlogic.gdx.graphics.Colors/put "MODIFIER_BLUE"
                                      Color/CYAN
                                      ; maybe can be used in tooltip background is darker (from D2 copied color)
                                      #_(com.badlogic.gdx.graphics.Color. (float 0.48)
                                                                          (float 0.57)
                                                                          (float 1)
                                                                          (float 1)))

; For now no green/red color for positive/negative numbers
; as :stats/damage-receive negative value would be red but actually a useful buff
; -> could give damage reduce 10% like in diablo 2
; and then make it negative .... @ applicator
(def ^:private positive-modifier-color "[MODIFIER_BLUE]" #_"[LIME]")
(def ^:private negative-modifier-color "[MODIFIER_BLUE]" #_"[SCARLET]")

(defn mod-info-text [modifiers]
  (str "[MODIFIER_BLUE]"
       (str/join "\n"
                 (for [[modifier-k operations] modifiers
                       operation operations]
                   (str (op-info-text operation) " " (k->pretty-name modifier-k))))
       "[]"))

(defcomponent :entity/modifiers
  {:data [:components-ns :modifier]
   :let modifiers}
  (->mk [_ _ctx]
    (into {} (for [[modifier-k operations] modifiers]
               [modifier-k (into {} (for [[operation-k value] operations]
                                      [operation-k [value]]))])))

  (info-text [_ _ctx]
    (let [modifiers (sum-operation-values modifiers)]
      (when (seq modifiers)
        (mod-info-text modifiers)))))

(extend-type core.entity.Entity
  core.entity/Modifiers
  (->modified-value [{:keys [entity/modifiers]} modifier-k base-value]
    {:pre [(= "modifier" (namespace modifier-k))]}
    (->> modifiers
         modifier-k
         (sort-by op-order)
         (reduce (fn [base-value [operation-k values]]
                   (op-apply [operation-k (apply + values)] base-value))
                 base-value))))

(comment
 (require '[core.entity :refer [->modified-value]])
 (let [->entity (fn [modifiers]
                  (core.entity/map->Entity {:entity/modifiers modifiers}))]
   (and
    (= (->modified-value (->entity {:modifier/damage-deal {:op/val-inc [30]
                                                           :op/val-mult [0.5]}})
                         :modifier/damage-deal
                         [5 10])
       [52 52])
    (= (->modified-value (->entity {:modifier/damage-deal {:op/val-inc [30]}
                                    :stats/fooz-barz {:op/babu [1 2 3]}})
                         :modifier/damage-deal
                         [5 10])
       [35 35])
    (= (->modified-value (core.entity/map->Entity {})
                         :modifier/damage-deal
                         [5 10])
       [5 10])
    (= (->modified-value (->entity {:modifier/hp {:op/max-inc [10 1]
                                                  :op/max-mult [0.5]}})
                         :modifier/hp
                         [100 100])
       [100 166])
    (= (->modified-value (->entity {:modifier/movement-speed {:op/inc [2]
                                                              :op/mult [0.1 0.2]}})
                         :modifier/movement-speed
                         3)
       6.5)))
 )

(defn- defmodifier [k operations]
  (defcomponent* k {:data [:map-optional operations]}))

(defn- stat-k->modifier-k [k]
  (keyword "modifier" (name k)))

(defn- stat-k->effect-k [k]
  (keyword "effect.entity" (name k)))

(defn- effect-k->stat-k [effect-k]
  (keyword "stats" (name effect-k)))

; is called :base/stat-effect so it doesn't show up in (:data [:components-ns :effect.entity]) list in editor
; for :skill/effects
(defcomponent :base/stat-effect
  (info-text [[k operations] _effect-ctx]
    (str/join "\n"
              (for [operation operations]
                (str (op-info-text operation) " " (k->pretty-name k)))))

  (applicable? [[k _] {:keys [effect/target]}]
    (and target
         (entity/stat @target (effect-k->stat-k k))))

  (useful? [_ _effect-ctx]
    true)

  (do! [[effect-k operations] {:keys [effect/target]}]
    (let [stat-k (effect-k->stat-k effect-k)]
      (when-let [effective-value (entity/stat @target stat-k)]
        [[:e/assoc-in target [:entity/stats stat-k]
          ; TODO similar to components.entity.modifiers/->modified-value
          ; but operations not sort-by op/order ??
          ; op-apply reuse fn over operations to get effectiv value
          (reduce (fn [value operation] (op-apply operation value))
                  effective-value
                  operations)]]))))

(defn- defstat [k {:keys [modifier-ops effect-ops] :as attr-m}]
  (defcomponent* k attr-m)
  (when modifier-ops
    (defmodifier (stat-k->modifier-k k) modifier-ops))
  (when effect-ops
    (let [effect-k (stat-k->effect-k k)]
      (defcomponent* effect-k {:data [:map-optional effect-ops]})
      (derive effect-k :base/stat-effect))))

; TODO needs to be there for each npc - make non-removable (added to all creatures)
; and no need at created player (npc controller component?)
(defstat :stats/aggro-range   {:data :nat-int})
(defstat :stats/reaction-time {:data :pos-int})

; TODO
; @ hp says here 'Minimum' hp instead of just 'HP'
; Sets to 0 but don't kills
; Could even set to a specific value ->
; op/set-to-ratio 0.5 ....
; sets the hp to 50%...
(defstat :stats/hp
  {:data :pos-int
   :modifier-ops [:op/max-inc :op/max-mult]
   :effect-ops [:op/val-inc :op/val-mult :op/max-inc :op/max-mult]})

(defstat :stats/mana
  {:data :nat-int
   :modifier-ops [:op/max-inc :op/max-mult]
   :effect-ops [:op/val-inc :op/val-mult :op/max-inc :op/max-mult]})

; * TODO clamp/post-process effective-values @ stat-k->effective-value
; * just don't create movement-speed increases too much?
; * dont remove strength <0 or floating point modifiers  (op/int-inc ?)
; * cast/attack speed dont decrease below 0 ??

; TODO clamp between 0 and max-speed ( same as movement-speed-schema )
(defstat :stats/movement-speed
  {:data :pos;(m/form entity/movement-speed-schema)
   :modifier-ops [:op/inc :op/mult]})

; TODO show the stat in different color red/green if it was permanently modified ?
; or an icon even on the creature
; also we want audiovisuals always ...
(defcomponent :effect.entity/movement-speed
  {:data [:map [:op/mult]]})
(derive :effect.entity/movement-speed :base/stat-effect)

; TODO clamp into ->pos-int
(defstat :stats/strength
  {:data :nat-int
   :modifier-ops [:op/inc]})

; TODO here >0
(let [doc "action-time divided by this stat when a skill is being used.
          Default value 1.

          For example:
          attack/cast-speed 1.5 => (/ action-time 1.5) => 150% attackspeed."
      data :pos
      operations [:op/inc]]
  (defstat :stats/cast-speed
    {:data data
     :editor/doc doc
     :modifier-ops operations})

  (defstat :stats/attack-speed
    {:data data
     :editor/doc doc
     :modifier-ops operations}))

; TODO bounds
(defstat :stats/armor-save
  {:data :number
   :modifier-ops [:op/inc]})

(defstat :stats/armor-pierce
  {:data :number
   :modifier-ops [:op/inc]})


(extend-type core.entity.Entity
  entity/Stats
  (stat [entity* stat-k]
    (when-let [base-value (stat-k (:entity/stats entity*))]
      (entity/->modified-value entity* (stat-k->modifier-k stat-k) base-value))))

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

(let [stats-order [:stats/hp
                   :stats/mana
                   ;:stats/movement-speed
                   :stats/strength
                   :stats/cast-speed
                   :stats/attack-speed
                   :stats/armor-save
                   ;:stats/armor-pierce
                   ;:stats/aggro-range
                   ;:stats/reaction-time
                   ]]
  (defn- stats-info-texts [entity*]
    (str/join "\n"
              (for [stat-k stats-order
                    :let [value (entity/stat entity* stat-k)]
                    :when value]
                (str (k->pretty-name stat-k) ": " value)))))

; TODO mana optional? / armor-save / armor-pierce (anyway wrong here)
; cast/attack-speed optional ?
(defcomponent :entity/stats
  {:data [:map [:stats/hp
                :stats/movement-speed
                :stats/aggro-range
                :stats/reaction-time
                [:stats/mana          {:optional true}]
                [:stats/strength      {:optional true}]
                [:stats/cast-speed    {:optional true}]
                [:stats/attack-speed  {:optional true}]
                [:stats/armor-save    {:optional true}]
                [:stats/armor-pierce  {:optional true}]]]
   :let stats}
  (->mk [_ _ctx]
    (-> stats
        (update :stats/hp (fn [hp] (when hp [hp hp])))
        (update :stats/mana (fn [mana] (when mana [mana mana])))))

  (info-text [_ {:keys [info-text/entity*]}]
    (stats-info-texts entity*))

  (entity/render-info [_ entity* g _ctx]
    (when-let [hp (entity/stat entity* :stats/hp)]
      (let [ratio (val-max-ratio hp)
            {:keys [position width half-width half-height entity/mouseover?]} entity*
            [x y] position]
        (when (or (< ratio 1) mouseover?)
          (let [x (- x half-width)
                y (+ y half-height)
                height (pixels->world-units g entity/hpbar-height-px)
                border (pixels->world-units g borders-px)]
            (draw-filled-rectangle g x y width height Color/BLACK)
            (draw-filled-rectangle g
                                   (+ x border)
                                   (+ y border)
                                   (- (* width ratio) (* 2 border))
                                   (- height (* 2 border))
                                   (hpbar-color ratio))))))))

(defcomponent :tx.entity.stats/pay-mana-cost
  (do! [[_ entity cost] _ctx]
    (let [mana-val ((entity/stat @entity :stats/mana) 0)]
      (assert (<= cost mana-val))
      [[:e/assoc-in entity [:entity/stats :stats/mana 0] (- mana-val cost)]])))

(comment
 (let [mana-val 4
       entity (atom (entity/map->Entity {:entity/stats {:stats/mana [mana-val 10]}}))
       mana-cost 3
       resulting-mana (- mana-val mana-cost)]
   (= (do! [:tx.entity.stats/pay-mana-cost entity mana-cost] nil)
      [[:e/assoc-in entity [:entity/stats :stats/mana 0] resulting-mana]]))
 )


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
  (info-text [_ {:keys [effect/source] :as effect-ctx}]
    (str "Damage based on entity strength."
         (when source
           (str "\n" (info-text (damage-effect effect-ctx) effect-ctx)))))

  (applicable? [_ effect-ctx]
    (applicable? (damage-effect effect-ctx) effect-ctx))

  (do! [_ ctx]
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

(defcomponent :damage/min-max {:data :val-max})

(defcomponent :effect.entity/damage
  {:let damage
   :data [:map [:damage/min-max]]}
  (info-text [_ {:keys [effect/source]}]
    (if source
      (let [modified (->effective-damage damage @source)]
        (if (= damage modified)
          (damage->text damage)
          (str (damage->text damage) "\nModified: " (damage->text modified))))
      (damage->text damage))) ; property menu no source,modifiers

  (applicable? [_ {:keys [effect/target]}]
    (and target
         (entity/stat @target :stats/hp)))

  (do! [_ {:keys [effect/source effect/target]}]
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
             dmg-amount (rand-int-between min-max)
             ;_ (println "dmg-amount: " dmg-amount)
             new-hp-val (max (- (hp 0) dmg-amount) 0)]
         [[:tx/audiovisual (:position target*) :audiovisuals/damage]
          [:tx/add-text-effect target (str "[RED]" dmg-amount)]
          [:e/assoc-in target [:entity/stats :stats/hp 0] new-hp-val]
          [:tx/event target (if (zero? new-hp-val) :kill :alert)]])))))
