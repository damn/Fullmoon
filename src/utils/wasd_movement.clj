(ns utils.wasd-movement
  (:require [gdl.context :refer [key-pressed?]]
            [gdl.input.keys :as input.keys]
            [gdl.math.vector :as v]))

(defn- add-vs [vs]
  (v/normalise (reduce v/add [0 0] vs)))

(defn WASD-movement-vector [ctx]
  (let [r (if (key-pressed? ctx input.keys/d) [1  0])
        l (if (key-pressed? ctx input.keys/a) [-1 0])
        u (if (key-pressed? ctx input.keys/w) [0  1])
        d (if (key-pressed? ctx input.keys/s) [0 -1])]
    (when (or r l u d)
      (let [v (add-vs (remove nil? [r l u d]))]
        (when (pos? (v/length v))
          v)))))
