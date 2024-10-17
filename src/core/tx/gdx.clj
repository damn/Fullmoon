(ns core.tx.gdx
  (:require [gdx.graphics :as g]
            [gdx.ui :as ui]
            [gdx.ui.actor :as a]
            [gdx.ui.stage-screen :refer [stage-add! stage-get]]
            [component.core :refer [defc]]
            [core.tx :as tx]))

; no window movable type cursor appears here like in player idle
; inventory still working, other stuff not, because custom listener to keypresses ? use actor listeners?
; => input events handling
; hmmm interesting ... can disable @ item in cursor  / moving / etc.
(defn- show-player-modal! [{:keys [title text button-text on-click]}]
  (assert (not (::modal (stage-get))))
  (stage-add! (ui/window {:title title
                          :rows [[(ui/label text)]
                                 [(ui/text-button button-text
                                                  (fn []
                                                    (a/remove! (::modal (stage-get)))
                                                    (on-click)))]]
                          :id ::modal
                          :modal? true
                          :center-position [(/ (g/gui-viewport-width) 2)
                                            (* (g/gui-viewport-height) (/ 3 4))]
                          :pack? true})))

(defc :tx/player-modal
  (tx/do! [[_ params]]
    (show-player-modal! params)
    nil))
