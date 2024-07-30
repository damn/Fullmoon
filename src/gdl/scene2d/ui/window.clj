(ns gdl.scene2d.ui.window)

(defprotocol Actor
  (window-title-bar? [_] "Returns true if the actor is a window title bar."))
