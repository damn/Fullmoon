(ns world.entity.stats
  (:require [clojure.gdx.graphics :as g]
            [clojure.string :as str]
            [core.component :refer [defc defc*]]
            [core.effect :as effect :refer [target]]
            [core.info :as info]
            [core.operation :as op]
            [core.tx :as tx]
            [core.val-max :as val-max]
            [utils.core :refer [k->pretty-name]]
            [world.entity :as entity]
            [world.entity.modifiers :refer [->modified-value]]))

(defn- defmodifier [k operations]
  (defc* k {:data [:map-optional operations]}))

(defn- stat-k->modifier-k [k]
  (keyword "modifier" (name k)))

(defn- stat-k->effect-k [k]
  (keyword "effect.entity" (name k)))

(defn effect-k->stat-k [effect-k]
  (keyword "stats" (name effect-k)))

(defn defstat [k {:keys [modifier-ops effect-ops] :as attr-m}]
  (defc* k attr-m)
  (when modifier-ops
    (defmodifier (stat-k->modifier-k k) modifier-ops))
  (when effect-ops
    (let [effect-k (stat-k->effect-k k)]
      (defc* effect-k {:data [:map-optional effect-ops]})
      (derive effect-k :base/stat-effect))))

(defn entity-stat
  "Calculating value of the stat w. modifiers"
  [entity* stat-k]
  (when-let [base-value (stat-k (:entity/stats entity*))]
    (->modified-value entity* (stat-k->modifier-k stat-k) base-value)))

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

(defc :tx.entity.stats/pay-mana-cost
  (tx/do! [[_ eid cost]]
    (let [mana-val ((entity-stat @eid :stats/mana) 0)]
      (assert (<= cost mana-val))
      [[:e/assoc-in eid [:entity/stats :stats/mana 0] (- mana-val cost)]])))

(comment
 (let [mana-val 4
       eid (atom (entity/map->Entity {:entity/stats {:stats/mana [mana-val 10]}}))
       mana-cost 3
       resulting-mana (- mana-val mana-cost)]
   (= (tx/do! [:tx.entity.stats/pay-mana-cost eid mana-cost] nil)
      [[:e/assoc-in eid [:entity/stats :stats/mana 0] resulting-mana]]))
 )


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
(defc :effect.entity/movement-speed
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
                    :let [value (entity-stat entity* stat-k)]
                    :when value]
                (str (k->pretty-name stat-k) ": " value)))))

; TODO mana optional? / armor-save / armor-pierce (anyway wrong here)
; cast/attack-speed optional ?
(defc :entity/stats
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
  (entity/->v [_]
    (-> stats
        (update :stats/hp (fn [hp] (when hp [hp hp])))
        (update :stats/mana (fn [mana] (when mana [mana mana])))))

  (info/text [_]
    (stats-info-texts info/*info-text-entity*))

  (entity/render-info [_ entity*]
    (when-let [hp (entity-stat entity* :stats/hp)]
      (let [ratio (val-max/ratio hp)
            {:keys [position width half-width half-height entity/mouseover?]} entity*
            [x y] position]
        (when (or (< ratio 1) mouseover?)
          (let [x (- x half-width)
                y (+ y half-height)
                height (g/pixels->world-units 5)
                border (g/pixels->world-units borders-px)]
            (g/draw-filled-rectangle x y width height :black)
            (g/draw-filled-rectangle (+ x border)
                                     (+ y border)
                                     (- (* width ratio) (* 2 border))
                                     (- height (* 2 border))
                                     (hpbar-color ratio))))))))

; TODO negate this value also @ use
; so can make positiive modifeirs green , negative red....
(defmodifier :modifier/damage-receive [:op/max-inc :op/max-mult])
(defmodifier :modifier/damage-deal [:op/val-inc :op/val-mult :op/max-inc :op/max-mult])

; is called :base/stat-effect so it doesn't show up in (:data [:components-ns :effect.entity]) list in editor
; for :skill/effects
(defc :base/stat-effect
  (info/text [[k operations]]
    (str/join "\n"
              (for [operation operations]
                (str (op/info-text operation) " " (k->pretty-name k)))))

  (effect/applicable? [[k _]]
    (and target
         (entity-stat @target (effect-k->stat-k k))))

  (effect/useful? [_]
    true)

  (tx/do! [[effect-k operations]]
    (let [stat-k (effect-k->stat-k effect-k)]
      (when-let [effective-value (entity-stat @target stat-k)]
        [[:e/assoc-in target [:entity/stats stat-k]
          ; TODO similar to components.entity.modifiers/->modified-value
          ; but operations not sort-by op/order ??
          ; op-apply reuse fn over operations to get effectiv value
          (reduce (fn [value operation] (op/apply operation value))
                  effective-value
                  operations)]]))))
