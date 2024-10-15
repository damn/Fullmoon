(ns world.entity.skills
  (:require [clojure.gdx.ui :as ui]
            [clojure.gdx.ui.actor :as a]
            [clojure.gdx.ui.stage-screen :refer [stage-get]]
            [core.component :refer [defc defsystem]]
            [core.info :as info]
            [core.property :as property]
            [core.tx :as tx]
            [utils.core :refer [readable-number]]
            [world.entity :as entity]
            [world.entity.state :as entity-state]
            [world.player :refer [world-player]]
            [world.time :refer [stopped?]]
            [world.widgets :refer [world-widgets]]))

(property/def :properties/skills
  {:schema [:entity/image
            :property/pretty-name
            :skill/action-time-modifier-key
            :skill/action-time
            :skill/start-action-sound
            :skill/effects
            [:skill/cooldown {:optional true}]
            [:skill/cost {:optional true}]]
   :overview {:title "Skills"
              :columns 16
              :image/scale 2}})

(defc :skill/action-time-modifier-key
  {:data [:enum :stats/cast-speed :stats/attack-speed]}
  (info/text [[_ v]]
    (str "[VIOLET]" (case v
                      :stats/cast-speed "Spell"
                      :stats/attack-speed "Attack") "[]")))

(defc :skill/action-time {:data :pos}
  (info/text [[_ v]]
    (str "[GOLD]Action-Time: " (readable-number v) " seconds[]")))

(defc :skill/start-action-sound {:data :sound})

(defc :skill/effects
  {:data [:components-ns :effect]})

(defc :skill/cooldown {:data :nat-int}
  (info/text [[_ v]]
    (when-not (zero? v)
      (str "[SKY]Cooldown: " (readable-number v) " seconds[]"))))

(defc :skill/cost {:data :nat-int}
  (info/text [[_ v]]
    (when-not (zero? v)
      (str "[CYAN]Cost: " v " Mana[]"))))

(defc :entity/skills
  {:data [:one-to-many :properties/skills]}
  (entity/create [[k skills] eid]
    (cons [:e/assoc eid k nil]
          (for [skill skills]
            [:tx/add-skill eid skill])))

  (info/text [[_ skills]]
    ; => recursive info-text leads to endless text wall
    #_(when (seq skills)
        (str "[VIOLET]Skills: " (str/join "," (map name (keys skills))) "[]")))

  (entity/tick [[k skills] eid]
    (for [{:keys [skill/cooling-down?] :as skill} (vals skills)
          :when (and cooling-down?
                     (stopped? cooling-down?))]
      [:e/assoc-in eid [k (:property/id skill) :skill/cooling-down?] false])))

(defn has-skill? [{:keys [entity/skills]} {:keys [property/id]}]
  (contains? skills id))

(defc :tx/add-skill
  (tx/do! [[_ eid {:keys [property/id] :as skill}]]
    (assert (not (has-skill? @eid skill)))
    [[:e/assoc-in eid [:entity/skills id] skill]
     (when (:entity/player? @eid)
       [:tx.action-bar/add skill])]))

(defc :tx/remove-skill
  (tx/do! [[_ eid {:keys [property/id] :as skill}]]
    (assert (has-skill? @eid skill))
    [[:e/dissoc-in eid [:entity/skills id]]
     (when (:entity/player? @eid)
       [:tx.action-bar/remove skill])]))

(defsystem clicked-skillmenu-skill [_ skill])
(defmethod clicked-skillmenu-skill :default [_ skill])

(defn- player-clicked-skillmenu [skill]
  (clicked-skillmenu-skill (entity-state/state-obj @world-player) skill))

; TODO render text label free-skill-points
; (str "Free points: " (:entity/free-skill-points @world-player))
#_(defn ->skill-window []
    (ui/window {:title "Skills"
                :id :skill-window
                :visible? false
                :cell-defaults {:pad 10}
                :rows [(for [id [:skills/projectile
                                 :skills/meditation
                                 :skills/spawn
                                 :skills/melee-attack]
                             :let [; get-property in callbacks if they get changed, this is part of context permanently
                                   button (ui/image-button ; TODO reuse actionbar button scale?
                                                           (:entity/image (db/get id)) ; TODO here anyway taken
                                                           ; => should probably build this window @ game start
                                                           (fn []
                                                             (tx/do-all (player-clicked-skillmenu (db/get id)))))]]
                         (do
                          (ui/add-tooltip! button #(info/->text (db/get id))) ; TODO no player modifiers applied (see actionbar)
                          button))]
                :pack? true}))

(def ^:private image-scale 2)

(defn ->action-bar []
  (let [group (ui/horizontal-group {:pad 2 :space 2})]
    (a/set-id! group ::action-bar)
    group))

(defn ->action-bar-button-group []
  (ui/button-group {:max-check-count 1 :min-check-count 0}))

(defn- get-action-bar []
  {:horizontal-group (::action-bar (:action-bar-table (stage-get)))
   :button-group (:action-bar world-widgets)})

(defc :tx.action-bar/add
  (tx/do! [[_ {:keys [property/id entity/image] :as skill}]]
    (let [{:keys [horizontal-group button-group]} (get-action-bar)
          button (ui/image-button image (fn []) {:scale image-scale})]
      (a/set-id! button id)
      (ui/add-tooltip! button #(info/->text skill)) ; (assoc ctx :effect/source (world-player)) FIXME
      (ui/add-actor! horizontal-group button)
      (ui/bg-add! button-group button)
      nil)))

(defc :tx.action-bar/remove
  (tx/do! [[_ {:keys [property/id]}]]
    (let [{:keys [horizontal-group button-group]} (get-action-bar)
          button (get horizontal-group id)]
      (a/remove! button)
      (ui/bg-remove! button-group button)
      nil)))

(defn selected-skill []
  (let [button-group (:action-bar world-widgets)]
    (when-let [skill-button (ui/bg-checked button-group)]
      (a/id skill-button))))

(comment

 (comment
  (def sword-button (.getChecked button-group))
  (.setChecked sword-button false)
  )

 #_(defn- number-str->input-key [number-str]
     (eval (symbol (str "com.badlogic.gdx.Input$Keys/NUM_" number-str))))

 ; TODO do with an actor
 ; .getChildren horizontal-group => in order
 (defn up-skill-hotkeys []
   #_(doseq [slot slot-keys
             :let [skill-id (slot @slot->skill-id)]
             :when (and (key-just-pressed? (number-str->input-key (name slot)))
                        skill-id)]
       (.setChecked ^Button (.findActor horizontal-group (str skill-id)) true)))

 ; TODO
 ; * cooldown / not usable -> diff. colors ? disable on not able to use skills (stunned?)
 ; * or even sector circling for cooldown like in WoW (clipped !)
 ; * tooltips ! with hotkey-number !
 ;  ( (skills/text skill-id world-player))
 ; * add hotkey number to tooltips
 ; * hotkeys => select button
 ; when no selected-skill & new skill assoce'd (sword at start)
 ; => set selected
 ; keep weapon at position 1 always ?

 #_(def ^:private slot-keys {:1 input.keys.num-1
                             :2 input.keys.num-2
                             :3 input.keys.num-3
                             :4 input.keys.num-4
                             :5 input.keys.num-5
                             :6 input.keys.num-6
                             :7 input.keys.num-7
                             :8 input.keys.num-8
                             :9 input.keys.num-9})

 #_(defn- empty-slot->skill-id []
     (apply sorted-map
            (interleave slot-keys
                        (repeat nil))))

 #_(def selected-skill-id (atom nil))
 #_(def ^:private slot->skill-id (atom nil))

 #_(defn reset-skills! []
     (reset! selected-skill-id nil)
     (reset! slot->skill-id (empty-slot->skill-id)))


 ; https://javadoc.io/doc/com.badlogicgames.gdx/gdx/latest/com/badlogic/gdx/scenes/scene2d/ui/Button.html
 (.setProgrammaticChangeEvents ^Button (.findActor horizontal-group ":skills/spawn") true)
 ; but doesn't toggle:
 (.toggle ^Button (.findActor horizontal-group ":skills/spawn"))
 (.setChecked ^Button (.findActor horizontal-group ":skills/spawn") true)
 ; Toggles the checked state. This method changes the checked state, which fires a ChangeListener.ChangeEvent (if programmatic change events are enabled), so can be used to simulate a button click.

 ; => it _worked_ => active skill changed
 ; only button is not highlighted idk why

 (.getChildren horizontal-group)

 )
