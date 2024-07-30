(ns gdl.math.geom
  "API wrapping com.badlogic.gdx.math.Intersector"
  (:import (com.badlogic.gdx.math Rectangle Circle Intersector)))

(defmulti ^:private collides?* (fn [a b] [(class a) (class b)]))

(defmethod collides?* [Circle Circle]
  [^Circle a ^Circle b]
  (Intersector/overlaps a b))

(defmethod collides?* [Rectangle Rectangle]
  [^Rectangle a ^Rectangle b]
  (Intersector/overlaps a b))

(defmethod collides?* [Rectangle Circle]
  [^Rectangle rect ^Circle circle]
  (Intersector/overlaps circle rect))

(defmethod collides?* [Circle Rectangle]
  [^Circle circle ^Rectangle rect]
  (Intersector/overlaps circle rect))

(defn- rectangle? [{[x y] :left-bottom :keys [width height]}]
  (and x y width height))

(defn- circle? [{[x y] :position :keys [radius]}]
  (and x y radius))

(defn- m->shape [m]
  (cond
   (rectangle? m) (let [{[x y] :left-bottom :keys [width height]} m]
                       (Rectangle. x y width height))

   (circle? m) (let [{[x y] :position :keys [radius]} m]
                    (Circle. x y radius))

   :else (throw (Error. (str m)))))

(defn collides? [a b]
  (collides?* (m->shape a) (m->shape b)))

(defn point-in-rect? [[x y] rectangle]
  (.contains ^Rectangle (m->shape rectangle) x y))

(defn circle->outer-rectangle [{[x y] :position :keys [radius] :as circle}]
  {:pre [(circle? circle)]}
  (let [radius (float radius)
        size (* radius 2)]
    {:left-bottom [(- (float x) radius)
                   (- (float y) radius)]
     :width  size
     :height size}))
