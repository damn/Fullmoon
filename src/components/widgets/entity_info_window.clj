(ns components.widgets.entity-info-window
  (:require [core.components :as components]
            [core.context :as ctx :refer [->actor ->window ->label]]
            [core.scene2d.ui.label :refer [set-text!]]
            [core.scene2d.group :refer [add-actor!]]
            [core.scene2d.ui.widget-group :refer [pack!]]))

(defn create [context]
  (let [label (->label context "")
        window (->window context {:title "Info"
                                  :id :entity-info-window
                                  :visible? false
                                  :position [(ctx/gui-viewport-width context) 0]
                                  :rows [[{:actor label :expand? true}]]})]
    ; TODO do not change window size ... -> no need to invalidate layout, set the whole stage up again
    ; => fix size somehow.
    (add-actor! window (->actor context {:act (fn update-label-text [ctx]
                                                (set-text! label
                                                           (when-let [entity* (ctx/mouseover-entity* ctx)]
                                                             (components/info-text entity* ctx)))
                                                (pack! window))}))
    window))
