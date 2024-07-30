(ns gdl.scene2d.ui.button-group)

(defprotocol ButtonGroup
  (clear! [_])
  (add! [_ button])
  (checked [_])
  (remove! [_ button]))
