(ns core.stat
  (:require [clojure.string :as str]
            [utils.core :as utils]
            [core.component :as component :refer [defcomponent defcomponent*]]
            [core.entity :as entity]
            [core.effect :as effect]
            [core.operation :as operation]
            [core.tx :as tx]))

(defn defmodifier [k operations]
  (defcomponent* k {:data [:map-optional operations]}))

(defn stat-k->modifier-k [k]
  (keyword "modifier" (name k)))

(defn stat-k->effect-k [k]
  (keyword "effect.entity" (name k)))

(defn- effect-k->stat-k [effect-k]
  (keyword "stats" (name effect-k)))

; is called :base/stat-effect so it doesn't show up in (:data [:components-ns :effect.entity]) list in editor
; for :skill/effects
(defcomponent :base/stat-effect
  (component/info-text [[k operations] _effect-ctx]
    (str/join "\n"
              (for [operation operations]
                (str (operation/info-text operation) " " (utils/k->pretty-name k)))))

  (effect/applicable? [[k _] {:keys [effect/target]}]
    (and target
         (entity/stat @target (effect-k->stat-k k))))

  (effect/useful? [_ _effect-ctx]
    true)

  (tx/do! [[effect-k operations] {:keys [effect/target]}]
    (let [stat-k (effect-k->stat-k effect-k)]
      (when-let [effective-value (entity/stat @target stat-k)]
        [[:tx/assoc-in target [:entity/stats stat-k]
          ; TODO similar to components.entity.modifiers/->modified-value
          ; but operations not sort-by operation/order ??
          ; operation/apply reuse fn over operations to get effectiv value
          (reduce (fn [value operation] (operation/apply operation value))
                  effective-value
                  operations)]]))))

(defn defstat [k {:keys [modifier-ops effect-ops] :as attr-m}]
  (defcomponent* k attr-m)
  (when modifier-ops
    (defmodifier (stat-k->modifier-k k) modifier-ops))
  (when effect-ops
    (let [effect-k (stat-k->effect-k k)]
      (defcomponent* effect-k {:data [:map-optional effect-ops]})
      (derive effect-k :base/stat-effect))))
