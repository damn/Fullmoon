(ns api.scene2d.actor
  (:refer-clojure :exclude [name])
  (:require [api.scene2d.ui.widget-group :refer [pack!]]
            app)
  (:import com.badlogic.gdx.utils.Align
           (com.badlogic.gdx.scenes.scene2d Actor Touchable)
           com.badlogic.gdx.scenes.scene2d.ui.Window
           (com.kotcrab.vis.ui.widget VisLabel Tooltip)))

(defn id [^Actor actor]
  (.getUserObject actor))

(defn set-id! [^Actor actor id]
  (.setUserObject actor id))

(defn set-name! [^Actor actor name]
  (.setName actor name))

(defn name [^Actor actor]
  (.getName actor))

(defn visible? [^Actor actor]
  (.isVisible actor))

(defn set-visible! [^Actor actor bool]
  (.setVisible actor (boolean bool)))

(defn toggle-visible! [actor]
  (set-visible! actor (not (visible? actor))))

(defn set-position! [^Actor actor x y]
  (.setPosition actor x y))

(defn width  [^Actor actor] (.getWidth actor))
(defn height [^Actor actor] (.getHeight actor))

(defn set-center! [actor x y]
  (set-position! actor
                 (- x (/ (width actor) 2))
                 (- y (/ (height actor) 2))))

(defn set-width!  [^Actor actor width]  (.setWidth  actor width))
(defn set-height! [^Actor actor height] (.setHeight actor height))

(defn get-x [^Actor actor] (.getY actor))
(defn get-y [^Actor actor] (.getX actor))

(defn set-touchable!
  ":children-only, :disabled or :enabled."
  [^Actor actor touchable]
  (.setTouchable actor (case touchable
                         :children-only Touchable/childrenOnly
                         :disabled      Touchable/disabled
                         :enabled       Touchable/enabled)))

(defn add-listener! [^Actor actor listener]
  (.addListener actor listener))

(defn remove!
  "Removes this actor from its parent, if it has a parent."
  [^Actor actor]
  (.remove actor))

(defn parent
  "Returns the parent actor, or null if not in a group."
  [^Actor actor]
  (.getParent actor))

(defn add-tooltip!
  "tooltip-text is a (fn [context] ) or a string. If it is a function will be-recalculated every show."
  [^Actor actor tooltip-text]
  (let [text? (string? tooltip-text)
        label (VisLabel. (if text? tooltip-text ""))
        tooltip (proxy [Tooltip] []
                  ; hooking into getWidth because at
                  ; https://github.com/kotcrab/vis-ui/blob/master/ui/src/main/java/com/kotcrab/vis/ui/widget/Tooltip.java#L271
                  ; when tooltip position gets calculated we setText (which calls pack) before that
                  ; so that the size is correct for the newly calculated text.
                  (getWidth []
                    (let [^Tooltip this this]
                      (when-not text?
                        (when-let [ctx @app/state]  ; initial tooltip creation when app context is getting built.
                          (.setText this (str (tooltip-text ctx)))))
                      (proxy-super getWidth))))]
    (.setAlignment label Align/center)
    (.setTarget  tooltip ^Actor actor)
    (.setContent tooltip ^Actor label)))

(defn remove-tooltip! [^Actor actor]
  (Tooltip/removeTooltip actor))

(defn find-ancestor-window [^Actor actor]
  (if-let [p (parent actor)]
    (if (instance? Window p)
      p
      (find-ancestor-window p))
    (throw (Error. (str "Actor has no parent window " actor)))))

(defn pack-ancestor-window! [^Actor actor]
  (pack! (find-ancestor-window actor)))
