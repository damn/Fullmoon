(ns gdl.scene2d.ui.button)

(defprotocol Actor
  (button? [_] "Returns true if the actor is a button."))
