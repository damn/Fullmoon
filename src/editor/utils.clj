(ns editor.utils
  (:require [gdx.graphics :as g]
            [gdx.ui :as ui]))

; TODO set to preferred width/height ??? why layouting not working properly?
; use a tree?
; make example with plain data
(defn scroll-pane-cell [rows]
  (let [table (ui/table {:rows rows :cell-defaults {:pad 1} :pack? true})
        scroll-pane (ui/scroll-pane table)]
    {:actor scroll-pane
     :width  (- (g/gui-viewport-width)  600)    ; (+ (actor/width table) 200)
     :height (- (g/gui-viewport-height) 100)})) ; (min (- (g/gui-viewport-height) 50) (actor/height table))

(defn scrollable-choose-window [rows]
  (ui/window {:title "Choose"
              :modal? true
              :close-button? true
              :center? true
              :close-on-escape? true
              :rows [[(scroll-pane-cell rows)]]
              :pack? true}))
