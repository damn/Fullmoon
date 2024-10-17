(ns moon.widgets.action-bar
  (:require [component.core :refer [defc defsystem]]
            [component.info :as info]
            [component.tx :as tx]
            [gdx.ui :as ui]
            [gdx.ui.actor :as a]
            [gdx.ui.stage-screen :refer [stage-get]]))

(def ^:private image-scale 2)

(defn- action-bar-button-group []
  (let [actor (ui/actor {})]
    (.setName actor "action-bar/button-group")
    (.setUserObject actor (ui/button-group {:max-check-count 1 :min-check-count 0}))
    actor))

(defn- group->button-group [group]
  (.getUserObject (.findActor group "action-bar/button-group")))

(defn create []
  (let [group (ui/horizontal-group {:pad 2 :space 2})]
    (a/set-id! group ::action-bar)
    (ui/add-actor! group (action-bar-button-group))
    group))

(defn- get-action-bar []
  (let [group (::action-bar (:action-bar-table (stage-get)))]
    {:horizontal-group group
     :button-group (group->button-group group)}))

(defc :tx.action-bar/add
  (tx/do! [[_ {:keys [property/id entity/image] :as skill}]]
    (let [{:keys [horizontal-group button-group]} (get-action-bar)
          button (ui/image-button image (fn []) {:scale image-scale})]
      (a/set-id! button id)
      (ui/add-tooltip! button #(info/->text skill)) ; (assoc ctx :effect/source (world/player)) FIXME
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
  (when-let [skill-button (ui/bg-checked (:button-group (get-action-bar)))]
    (a/id skill-button)))

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
 ;  ( (skills/text skill-id world/player))
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
