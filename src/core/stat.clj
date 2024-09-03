(ns core.stat
  (:require [core.component :refer [defcomponent*]]
            [core.modifier :refer [defmodifier]]))

(defn defmodifier [k operations]
  (defcomponent* k {:data [:components operations]}))

(defn stat-k->modifier-k [k]
  (keyword "modifier" (name k)))

(defn defstat [k {:keys [modifier-operations] :as attr-m}]
  (defcomponent* k attr-m)
  (when modifier-operations
    (defmodifier (stat-k->modifier-k k) modifier-operations)))
