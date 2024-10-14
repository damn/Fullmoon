(ns core.tx.gdx
  (:require [clojure.gdx.audio :refer [play-sound!]]
            [clojure.gdx.graphics :as g]
            [clojure.gdx.ui :as ui]
            [clojure.gdx.ui.actor :as a]
            [clojure.gdx.ui.stage-screen :refer [stage-add! stage-get]]
            [core.component :refer [defc]]
            [core.tx :as tx]))

(defc :tx/sound
  {:data :sound}
  (tx/do! [[_ file]]
    (play-sound! file)
    nil))

(defc :tx/cursor
  (tx/do! [[_ cursor-key]]
    (g/set-cursor! cursor-key)
    nil))

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
