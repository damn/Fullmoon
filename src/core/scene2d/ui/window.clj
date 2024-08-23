(ns core.scene2d.ui.window
  (:require [core.scene2d.actor :as actor])
  (:import (com.badlogic.gdx.scenes.scene2d.ui Label Window)
           com.kotcrab.vis.ui.widget.VisWindow))

(defn window-title-bar?
  "Returns true if the actor is a window title bar."
  [actor]
  (when (instance? Label actor)
    (when-let [parent (actor/parent actor)]
      (when-let [parent (actor/parent parent)]
        (and (instance? VisWindow parent)
             (= (.getTitleLabel ^Window parent) actor))))))
