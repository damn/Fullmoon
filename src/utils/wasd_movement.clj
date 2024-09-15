(ns utils.wasd-movement
  (:require [gdx.input :as input]
            [math.vector :as v])
  (:import com.badlogic.gdx.Input$Keys))

(defn- add-vs [vs]
  (v/normalise (reduce v/add [0 0] vs)))

(defn WASD-movement-vector []
  (let [r (if (input/key-pressed? Input$Keys/D) [1  0])
        l (if (input/key-pressed? Input$Keys/A) [-1 0])
        u (if (input/key-pressed? Input$Keys/W) [0  1])
        d (if (input/key-pressed? Input$Keys/S) [0 -1])]
    (when (or r l u d)
      (let [v (add-vs (remove nil? [r l u d]))]
        (when (pos? (v/length v))
          v)))))
