(ns utils.wasd-movement
  (:require [clj.gdx.input :as input]
            [clj.gdx.input.keys :as input.keys]
            [math.vector :as v]))

(defn- add-vs [vs]
  (v/normalise (reduce v/add [0 0] vs)))

(defn WASD-movement-vector [_ctx]
  (let [r (if (input/key-pressed? input.keys/d) [1  0])
        l (if (input/key-pressed? input.keys/a) [-1 0])
        u (if (input/key-pressed? input.keys/w) [0  1])
        d (if (input/key-pressed? input.keys/s) [0 -1])]
    (when (or r l u d)
      (let [v (add-vs (remove nil? [r l u d]))]
        (when (pos? (v/length v))
          v)))))
