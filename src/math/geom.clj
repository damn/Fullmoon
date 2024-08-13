(ns math.geom
  (:require [gdx.math :as math]))

(defn- rectangle? [{[x y] :left-bottom :keys [width height]}]
  (and x y width height))

(defn- circle? [{[x y] :position :keys [radius]}]
  (and x y radius))

(defn- m->shape [m]
  (cond
   (rectangle? m) (let [{:keys [left-bottom width height]} m]
                    (math/->rectangle left-bottom width height))

   (circle? m) (let [{:keys [position radius]} m]
                 (math/->circle position radius))

   :else (throw (Error. (str m)))))

(defn collides? [a b]
  (math/overlaps? (m->shape a) (m->shape b)))

(defn point-in-rect? [point rectangle]
  (math/contains? (m->shape rectangle) point))

(defn circle->outer-rectangle [{[x y] :position :keys [radius] :as circle}]
  {:pre [(circle? circle)]}
  (let [radius (float radius)
        size (* radius 2)]
    {:left-bottom [(- (float x) radius)
                   (- (float y) radius)]
     :width  size
     :height size}))
