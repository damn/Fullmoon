(ns context.ui.entity-info-window
  (:require [api.context :as ctx :refer [->actor ->window ->label]]
            [gdl.scene2d.ui.label :refer [set-text!]]
            [gdl.scene2d.group :refer [add-actor!]]
            [gdl.scene2d.ui.widget-group :refer [pack!]]
            [api.entity :as entity]))

(defn- entity-info-text [entity*]
  (binding [*print-level* nil]
    (with-out-str
     (clojure.pprint/pprint
      #_{:uid   (:entity/uid entity*)
       :state (entity/state entity*)
       :faction (:entity/faction entity*)
       :stats (:entity/stats entity*)}
      (select-keys entity*
                   [
                    ; TODO add 'name!
                    :entity/hp ; green, orange, red  bar
                    :entity/mana ; blue bar
                    :entity/item ; show or not ?
                    :entity/delete-after-duration  ; bar like in wc3 blue ? projec.
                    :entity/faction
                    :entity/projectile-collision ; -> hit-effect, piercing ? ...
                    :entity/reaction-time
                    ;:entity/inventory
                    ;:entity/skills
                    ;:entity/state
                    :entity/stats ; damage stats nested map ... not showing ...
                    ; TODO show a horizontal table like in wh40k ? keep stats simple only AS,Strength,Hp,Mana ... ?
                    ; no extra stats ? only modifier of that ?!

                    ])))))

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
