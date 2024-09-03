(ns core.stat
  (:require [core.component :refer [defcomponent*]]))

(defn stat-k->modifier-k [k]
  (keyword "modifier" (name k)))

(defn defstat [k {:keys [operations] :as attr-m}]
  (defcomponent* k attr-m)
  (when operations
    (defmodifier (stat-k->modifier-k k) operations)))
