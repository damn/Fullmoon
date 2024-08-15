(ns widgets.entity-info-window
  (:require [clojure.string :as str]
            [api.context :as ctx :refer [->actor ->window ->label]]
            [api.scene2d.ui.label :refer [set-text!]]
            [api.scene2d.group :refer [add-actor!]]
            [api.scene2d.ui.widget-group :refer [pack!]]
            [api.entity :as entity]))

; TODO each component & sub-component has info-text and colors again
; e.g. hit-effect @ projectile-collision is similar somewhere else ....
; or each stat/stat-modifier is itself a component with component/info-text
; => super simple & easy ....
; grep for: str/join "\n"
; and then add small ICONS/extra widgets (e.g. progress bar for delete after duration ) !!! => fatafoooobabababbuuu
; * fixed Reihenfolge
(defn- entity-info-text [entity* ctx]
  (->> entity*
       (keep #(entity/info-text % ctx))
       (str/join "\n")))

(defn create [context]
  (let [label (->label context "")
        window (->window context {:title "Info"
                                  :id :entity-info-window
                                  :visible? false
                                  :position [(ctx/gui-viewport-width context) 0]
                                  :rows [[{:actor label :expand? true}]]})]
    ; TODO do not change window size ... -> no need to invalidate layout, set the whole stage up again
    ; => fix size somehow.
    (add-actor! window (->actor context
                                {:act (fn [context]
                                        (set-text! label
                                                   (when-let [entity* (ctx/mouseover-entity* context)]
                                                     (entity-info-text entity* context)))
                                        (pack! window))}))
    window))
