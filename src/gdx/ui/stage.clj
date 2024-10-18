(ns gdx.ui.stage
  (:require [gdx.ui :refer [find-actor-with-id]])
  (:import (com.badlogic.gdx.scenes.scene2d Stage)))

(defn act!   [^Stage s]   (.act      s))
(defn draw!  [^Stage s]   (.draw     s))
(defn root   [^Stage s]   (.getRoot  s))
(defn clear! [^Stage s]   (.clear    s))
(defn add!   [^Stage s a] (.addActor s a))
(defn hit    [^Stage s [x y] & {:keys [touchable?]}]
  (.hit s x y (boolean touchable?)))

(defn create
  "Stage implements clojure.lang.ILookup (get) on actor id."
  ^Stage [viewport batch]
  (proxy [Stage clojure.lang.ILookup] [viewport batch]
    (valAt
      ([id]
       (find-actor-with-id (root this) id))
      ([id not-found]
       (or (find-actor-with-id (root this) id)
           not-found)))))
