(ns components.widgets.entity-info-window
  (:require [clojure.string :as str]
            [core.component :as component]
            [core.context :as ctx :refer [->actor ->window ->label]]
            [core.scene2d.ui.label :refer [set-text!]]
            [core.scene2d.group :refer [add-actor!]]
            [core.scene2d.ui.widget-group :refer [pack!]]
            [core.entity :as entity]))

; TODO each component & sub-component has info-text and colors again
; e.g. hit-effect @ projectile-collision is similar somewhere else ....
; or each stat/stat-modifier is itself a component with component/info-text
; => super simple & easy ....
; grep for: str/join "\n"
; and then add small ICONS/extra widgets (e.g. progress bar for delete after duration ) !!! => fatafoooobabababbuuu
; * fixed Reihenfolge

(def ^:private info-text-key-order
  [:entity.creature/name
   :entity.creature/species
   ;:entity/faction
   :entity/state
   :entity/stats ; TODO don't need to show movement-speed actually
   :entity/skills
   ;;
   :entity/delete-after-duration
   :entity/projectile-collision])

(defn- entity-info-text [entity* ctx]
  (str/join "\n"
            (for [k info-text-key-order
                  :let [component (k entity*)
                        text (when component
                               (component/info-text [k component] ctx))]
                  :when text]
              text)))

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
