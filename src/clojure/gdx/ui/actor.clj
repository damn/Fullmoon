(ns clojure.gdx.ui.actor
  (:refer-clojure :exclude [name])
  (:import (com.badlogic.gdx.math Vector2)
           (com.badlogic.gdx.scenes.scene2d Actor Touchable)))

(defn x             [^Actor a]      (.getX          a))
(defn y             [^Actor a]      (.getY          a))
(defn id            [^Actor a]      (.getUserObject a))
(defn name          [^Actor a]      (.getName       a))
(defn visible?      [^Actor a]      (.isVisible     a))
(defn set-id!       [^Actor a id]   (.setUserObject a id))
(defn set-name!     [^Actor a name] (.setName       a name))
(defn set-visible!  [^Actor a bool] (.setVisible    a (boolean bool)))
(defn set-position! [^Actor a x y]  (.setPosition   a x y))

(defn toggle-visible! [a]
  (set-visible! a (not (visible? a))))

(defn set-center! [^Actor a x y]
  (set-position! a
                 (- x (/ (.getWidth a) 2))
                 (- y (/ (.getHeight a) 2))))

(defn set-touchable!
  ":children-only, :disabled or :enabled."
  [^Actor a touchable]
  (.setTouchable a (case touchable
                         :children-only Touchable/childrenOnly
                         :disabled      Touchable/disabled
                         :enabled       Touchable/enabled)))

(defn add-listener! [^Actor a listener]
  (.addListener a listener))

(defn remove!
  "Removes this actor from its parent, if it has a parent."
  [^Actor a]
  (.remove a))

(defn parent
  "Returns the parent actor, or null if not in a group."
  [^Actor a]
  (.getParent a))

(defn mouseover? [^Actor a [x y]]
  (let [v (.stageToLocalCoordinates a (Vector2. x y))]
    (.hit a (.x v) (.y v) true)))

(defn set-opts! [a {:keys [id name visible? touchable center-position position] :as opts}]
  (when id                          (set-id!        a id))
  (when name                        (set-name!      a name))
  (when (contains? opts :visible?)  (set-visible!   a visible?))
  (when touchable                   (set-touchable! a touchable))
  (when-let [[x y] center-position] (set-center!    a x y))
  (when-let [[x y] position]        (set-position!  a x y))
  a)
