(ns world.entity.body
  (:require [utils.core :refer [->tile]]))

(defn tile [entity*]
  (->tile (:position entity*)))

