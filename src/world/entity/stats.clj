(ns world.entity.stats
  (:require [clojure.string :as str]
            [component.core :refer [defc defc*]]
            [component.info :as info]
            [component.operation :as op]
            [component.tx :as tx]
            [data.val-max :as val-max]
            [utils.core :refer [k->pretty-name]]
            [world.entity :as entity]
            [world.entity.hpbar :as hpbar]
            [world.entity.modifiers :refer [->modified-value]]
            [world.effect :as effect]))

(defn- defmodifier [k operations]
  (defc* k {:schema [:s/map-optional operations]}))

(defn-  stat-k->effect-k   [k] (keyword "effect.entity" (name k)))
(defn effect-k->stat-k     [k] (keyword "stats"         (name k)))
(defn-  stat-k->modifier-k [k] (keyword "modifier"      (name k)))

(defn entity-stat
  "Calculating value of the stat w. modifiers"
  [entity stat-k]
  (when-let [base-value (stat-k (:entity/stats entity))]
    (->modified-value entity (stat-k->modifier-k stat-k) base-value)))

; is called :base/stat-effect so it doesn't show up in (:schema [:s/components-ns :effect.entity]) list in editor
; for :skill/effects
(defc :base/stat-effect
  (info/text [[k operations]]
    (str/join "\n"
              (for [operation operations]
                (str (op/info-text operation) " " (k->pretty-name k)))))

  (effect/applicable? [[k _]]
    (and effect/target
         (entity-stat @effect/target (effect-k->stat-k k))))

  (effect/useful? [_]
    true)

  (tx/do! [[effect-k operations]]
    (let [stat-k (effect-k->stat-k effect-k)]
      (when-let [effective-value (entity-stat @effect/target stat-k)]
        [[:e/assoc-in effect/target [:entity/stats stat-k]
          ; TODO similar to components.entity.modifiers/->modified-value
          ; but operations not sort-by op/order ??
          ; op-apply reuse fn over operations to get effectiv value
          (reduce (fn [value operation] (op/apply operation value))
                  effective-value
                  operations)]]))))

(defn defstat [k {:keys [modifier-ops effect-ops] :as attr-m}]
  (defc* k attr-m)
  (when modifier-ops
    (defmodifier (stat-k->modifier-k k) modifier-ops))
  (when effect-ops
    (let [effect-k (stat-k->effect-k k)]
      (defc* effect-k {:schema [:s/map-optional effect-ops]})
      (derive effect-k :base/stat-effect))))

(load "stats_impl")

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
  (defn- stats-info-texts [entity]
    (str/join "\n"
              (for [stat-k stats-order
                    :let [value (entity-stat entity stat-k)]
                    :when value]
                (str (k->pretty-name stat-k) ": " value)))))

(defc :entity/stats
  {:schema [:s/map [:stats/hp
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
        (update :stats/hp   (fn [hp  ] (when hp   [hp   hp])))
        (update :stats/mana (fn [mana] (when mana [mana mana])))))

  (info/text [_]
    (stats-info-texts info/*info-text-entity*))

  (entity/render-info [_ entity]
    (when-let [hp (entity-stat entity :stats/hp)]
      (let [ratio (val-max/ratio hp)]
        (when (or (< ratio 1) (:entity/mouseover? entity))
          (hpbar/draw entity ratio))))))
