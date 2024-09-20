(ns components.widgets.player-modal
  (:require [core.component :refer [defcomponent]]
            [core.context :as ctx :refer [get-stage add-to-stage!]]
            [gdx.scene2d.ui :as ui]
            [gdx.scene2d.actor :refer [remove!]]
            [core.tx :as tx]))

; TODO no window movable type cursor appears here like in player idle
; inventory still working, other stuff not, because custom listener to keypresses ? use actor listeners?
; => input events handling
; hmmm interesting ... can disable @ item in cursor  / moving / etc.

(defn- show-player-modal! [ctx {:keys [title text button-text on-click]}]
  (assert (not (::modal (get-stage ctx))))
  (add-to-stage! ctx
                 (ui/->window {:title title
                               :rows [[(ui/->label text)]
                                      [(ui/->text-button ctx
                                                         button-text
                                                         (fn [ctx]
                                                           (remove! (::modal (get-stage ctx)))
                                                           (on-click ctx)))]]
                               :id ::modal
                               :modal? true
                               :center-position [(/ (ctx/gui-viewport-width ctx) 2)
                                                 (* (ctx/gui-viewport-height ctx) (/ 3 4))]
                               :pack? true})))

(defcomponent :tx/player-modal
  (tx/do! [[_ params] ctx]
    (show-player-modal! ctx params)))
