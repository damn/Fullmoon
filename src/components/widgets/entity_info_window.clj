(ns components.widgets.entity-info-window
  (:require [clojure.string :as str]
            [core.component :as component]
            [core.components :as components]
            [core.context :as ctx :refer [->actor ->window ->label]]
            [core.scene2d.ui.label :refer [set-text!]]
            [core.scene2d.group :refer [add-actor!]]
            [core.scene2d.ui.widget-group :refer [pack!]]
            [core.entity :as entity]))


; TODO pull out => entity/type or just type ?
(def ^:private info-text-key-order
  [; Creature
   :entity.creature/name
   :entity.creature/species
   ;:entity/faction
   :entity/state
   :entity/stats
   ;:entity/skills ; => recursive info-text leads to endless text wall

   ; Projectile
   :entity/delete-after-duration
   :entity/projectile-collision])

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
                                                             (components/info-text (select-keys entity* info-text-key-order)
                                                                                   ctx)))
                                                (pack! window))}))
    window))
