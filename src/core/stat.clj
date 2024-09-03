(ns core.stat
  (:require [core.component :refer [defcomponent*]]))

(defn defmodifier [k operations]
  (defcomponent* k {:data [:components operations]}))

(defn stat-k->modifier-k [k]
  (keyword "modifier" (name k)))

(defn defstat [k {:keys [modifier-ops] :as attr-m}]
  (defcomponent* k attr-m)
  (when modifier-ops
    (defmodifier (stat-k->modifier-k k) modifier-ops)))
