(ns widgets.entity-info-window
  (:require [api.context :as ctx :refer [->actor ->window ->label]]
            [api.scene2d.ui.label :refer [set-text!]]
            [api.scene2d.group :refer [add-actor!]]
            [api.scene2d.ui.widget-group :refer [pack!]]
            [api.entity :as entity]))

; for a list of keys
; if the entity has that key
; draw that key ... ( skill as icons ? ....) armor w. icon ?

(defn- entity-info-text [entity*]
  (binding [*print-level* nil]
    (with-out-str
     (clojure.pprint/pprint
      (merge
       (select-keys entity*
                    [ ; TODO name / species / level
                     :entity/hp ; green, orange, red  bar
                     :entity/mana ; blue bar ( or remove if not there )
                     ; :entity/faction no need to show
                     ; :entity/flying? no need to show
                     ; :entity/movement no need to show speed
                     ; :entity/reaction-time no need to show
                     ;:entity/skills (too big)
                     ; :entity/item ; show or not ?
                     :entity/delete-after-duration  ; bar like in wc3 blue ? projec.
                     :entity/projectile-collision ; -> hit-effect, piercing ? ...
                     ;:entity/inventory (only player for now)
                     ; TODO :entity/skills, just icons .... ?
                     ;:entity/state
                     :entity/stats ; damage stats nested map ... not showing ...
                     ; TODO show a horizontal table like in wh40k ? keep stats simple only AS,Strength,Hp,Mana ... ?
                     ; no extra stats ? only modifier of that ?!

                     ])
       {:entity/skills (keys (:entity/skills entity*))
        ;:entity/state (entity/state entity*)
        }

             )))))

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
                                                   (when-let [entity @(:context/mouseover-entity context)]
                                                     (entity-info-text @entity)))
                                        (pack! window))}))
    window))
