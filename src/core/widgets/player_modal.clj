(ns ^:no-doc core.widgets.player-modal
  (:require [core.stage :as stage]
            [core.ctx :refer :all]
            [core.ui :as ui]
            [core.actor :refer [remove!]]))

; TODO no window movable type cursor appears here like in player idle
; inventory still working, other stuff not, because custom listener to keypresses ? use actor listeners?
; => input events handling
; hmmm interesting ... can disable @ item in cursor  / moving / etc.

(defn- show-player-modal! [ctx {:keys [title text button-text on-click]}]
  (assert (not (::modal (stage/get ctx))))
  (stage/add-actor! ctx
                    (ui/->window {:title title
                                  :rows [[(ui/->label text)]
                                         [(ui/->text-button button-text
                                                            (fn [ctx]
                                                              (remove! (::modal (stage/get ctx)))
                                                              (on-click ctx)))]]
                                  :id ::modal
                                  :modal? true
                                  :center-position [(/ (gui-viewport-width ctx) 2)
                                                    (* (gui-viewport-height ctx) (/ 3 4))]
                                  :pack? true})))

(defcomponent :tx/player-modal
  (do! [[_ params] ctx]
    (show-player-modal! ctx params)))
