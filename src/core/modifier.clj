(ns core.modifier
  (:require [core.component :refer [defcomponent*]]))

(defn defmodifier [k operations]
  (defcomponent* k {:data [:components operations]}))
