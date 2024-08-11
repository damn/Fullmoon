(ns widgets.entity-info-window
  (:require [clojure.string :as str]
            [api.context :as ctx :refer [->actor ->window ->label]]
            [api.scene2d.ui.label :refer [set-text!]]
            [api.scene2d.group :refer [add-actor!]]
            [api.scene2d.ui.widget-group :refer [pack!]]
            [api.entity :as entity]))

; given an ordered list of to be rendered keys
; calls the to-text function of that key
; and joins them with newlines ....

(defn- entity->text [{:keys [entity/skills
                             entity/projectile-collision]
                      {:keys [stats/hp
                              stats/mana
                              stats/movement-speed
                              stats/strength
                              stats/cast-speed
                              stats/attack-speed
                              stats/armor-save
                              stats/armor-pierce] :as stats} :entity/stats}]
  ; HP color based on ratio like hp bar samey
  ; mana color same in the whole app
  ; TODO name / species / level
  ; :entity/faction no need to show
  ; :entity/flying? no need to show
  ; :entity/reaction-time no need to show
  ; :entity/delete-after-duration  ; bar like in wc3 blue ? projec.
  ;:entity/inventory (only player for now)
  [(when (and stats hp) (str "[RED]Hitpoints: " (hp 0) " / " (hp 1)))
   (when (and stats mana) (str "[CYAN]Mana: " (mana 0) " / " (mana 1)))
   (when (and stats strength) (str "[WHITE]Strength: " strength))
   (when (and stats cast-speed) (str "[WHITE]Cast-Speed: " cast-speed))
   (when (and stats attack-speed) (str "[WHITE]Attack-Speed: " attack-speed))
   (when (and stats armor-save) (str "[WHITE]Armor-Save: " armor-save))
   (when (and stats armor-pierce) (str "[WHITE]Armor-Pierce: " armor-pierce))
   (when (and stats movement-speed) (str "[WHITE]Movement-Speed: " movement-speed))

   (when skills (str "[WHITE]Skills: " (str/join "," (keys skills))))
   (when projectile-collision (str "[LIME]Projectile: " projectile-collision))])


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
