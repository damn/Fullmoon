(ns clojure.gdx.math.vector
  (:import (com.badlogic.gdx.math MathUtils Vector2)))

(defn- ^Vector2 ->v [[x y]]
  (Vector2. x y))

(defn- ->p [^Vector2 v]
  [(.x ^Vector2 v) (.y ^Vector2 v)])

(defn scale [v n]
  (->p (.scl ^Vector2 (->v v) (float n)))) ; TODO just (mapv (partial * 2) v)

(defn normalise [v]
  (->p (.nor ^Vector2 (->v v))))

(defn add [v1 v2]
  (->p (.add ^Vector2 (->v v1) ^Vector2 (->v v2))))

(defn length [v]
  (.len ^Vector2 (->v v)))

(defn distance [v1 v2]
  (.dst ^Vector2 (->v v1) ^Vector2 (->v v2)))

(defn normalised? [v]
  (MathUtils/isEqual 1 (length v)))

(defn normal-vectors [[x y]]
  [[(- (float y))         x]
   [          y (- (float x))]])

(defn direction [[sx sy] [tx ty]]
  (normalise [(- (float tx) (float sx))
              (- (float ty) (float sy))]))

(defn angle-from-vector
  "converts theta of Vector2 to angle from top (top is 0 degree, moving left is 90 degree etc.), ->counterclockwise"
  [v]
  (.angleDeg (->v v) (Vector2. 0 1)))

(comment

 (pprint
  (for [v [[0 1]
           [1 1]
           [1 0]
           [1 -1]
           [0 -1]
           [-1 -1]
           [-1 0]
           [-1 1]]]
    [v
     (.angleDeg (->v v) (Vector2. 0 1))
     (get-angle-from-vector (->v v))]))

 )
