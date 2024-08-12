(ns widgets.entity-info-window
  (:require [clojure.string :as str]
            [api.context :as ctx :refer [->actor ->window ->label]]
            [api.scene2d.ui.label :refer [set-text!]]
            [api.scene2d.group :refer [add-actor!]]
            [api.scene2d.ui.widget-group :refer [pack!]]
            [api.entity :as entity]))

(def ^:private stats
  [:stats/hp
   :stats/mana
   :stats/movement-speed
   :stats/strength
   :stats/cast-speed
   :stats/attack-speed
   :stats/armor-save
   :stats/armor-pierce])

; HP color based on ratio like hp bar samey
; mana color same in the whole app
; TODO name / species / level
; :entity/faction no need to show
; :entity/flying? no need to show
; :entity/reaction-time no need to show
; :entity/delete-after-duration  ; bar like in wc3 blue ? projec.
; :entity/inventory (only player for now)
; TODO move this to the component itself together with ....
; TODO readable-number (8.3999999999 ) / proper pretty-names / .... red/green color
(defn- entity->text [{:keys [entity/skills
                             entity/projectile-collision]
                      :as entity*
                      {:keys [stats/modifiers]} :entity/stats}]
  (concat (for [stat stats]
            (str (name stat) ": " (or (entity/stat entity* stat) "nil")))
          [(str "[LIME] Modifiers:\n"
                (binding [*print-level* nil]
                  (with-out-str
                   (clojure.pprint/pprint (sort-by key modifiers)))))
           (when skills (str "[WHITE]Skills: " (str/join "," (keys skills))))
           (when projectile-collision (str "[LIME]Projectile: " projectile-collision))]))


(defn- entity-info-text [entity*]
  (->> entity*
       entity->text
       (remove nil?)
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
                                                     (entity-info-text entity*)))
                                        (pack! window))}))
    window))
