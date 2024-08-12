(ns clj.gdx.math
  (:refer-clojure :exclude [contains?])
  (:import (com.badlogic.gdx.math Circle
                                  Rectangle
                                  Intersector)))

(defn ->circle [[x y] radius]
  (Circle. x y radius))

(defn ->rectangle [[x y] width height]
  (Rectangle. x y width height))

(defn contains? [^Rectangle rectangle [x y]]
  (.contains rectangle x y))

(defmulti overlaps? (fn [a b] [(class a) (class b)]))

(defmethod overlaps? [Circle Circle]
  [^Circle a ^Circle b]
  (Intersector/overlaps a b))

(defmethod overlaps? [Rectangle Rectangle]
  [^Rectangle a ^Rectangle b]
  (Intersector/overlaps a b))

(defmethod overlaps? [Rectangle Circle]
  [^Rectangle rect ^Circle circle]
  (Intersector/overlaps circle rect))

(defmethod overlaps? [Circle Rectangle]
  [^Circle circle ^Rectangle rect]
  (Intersector/overlaps circle rect))

