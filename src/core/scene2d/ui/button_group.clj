(ns core.scene2d.ui.button-group
  (:import (com.badlogic.gdx.scenes.scene2d.ui Button
                                               ButtonGroup)))

(defn clear! [^ButtonGroup button-group]
  (.clear button-group))

(defn checked [^ButtonGroup button-group]
  (.getChecked button-group))

(defn add! [^ButtonGroup button-group button]
  (.add button-group ^Button button))

(defn remove! [^ButtonGroup button-group button]
  (.remove button-group ^Button button))
