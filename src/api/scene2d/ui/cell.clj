(ns api.scene2d.ui.cell)

(defprotocol Cell
  (set-actor! [_ actor]))
