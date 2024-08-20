(ns widgets.player-modal
  (:require [api.context :as ctx :refer [get-stage ->window ->label ->text-button add-to-stage!]]
            [api.scene2d.actor :refer [remove!]]
            [api.effect :as effect]))

; TODO no window movable type cursor appears here like in player idle
; inventory still working, other stuff not, because custom listener to keypresses ? use actor listeners?
; => input events handling
; hmmm interesting ... can disable @ item in cursor  / moving / etc.

(defn- show-player-modal! [ctx {:keys [title text button-text on-click]}]
  (assert (not (::modal (get-stage ctx))))
  (add-to-stage! ctx
                 (->window ctx {:title title
                                :rows [[(->label ctx text)]
                                       [(->text-button ctx
                                                       button-text
                                                       (fn [ctx]
                                                         (remove! (::modal (get-stage ctx)))
                                                         (on-click ctx)))]]
                                :id ::modal
                                :modal? true
                                :center-position [(/ (ctx/gui-viewport-width ctx) 2)
                                                  (* (ctx/gui-viewport-height ctx) (/ 3 4))]
                                :pack? true})))

(defmethod effect/do! :tx/player-modal [[_ params] ctx]
  (show-player-modal! ctx params)
  ctx)
