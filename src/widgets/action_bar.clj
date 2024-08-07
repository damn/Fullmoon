(ns widgets.action-bar
  (:require [api.context :as ctx :refer [->image-button key-just-pressed? player-tooltip-text]]
            [api.scene2d.actor :as actor :refer [remove! add-tooltip!]]
            [api.scene2d.group :refer [clear-children! add-actor!]]
            [api.scene2d.ui.button-group :refer [clear! add! checked] :as button-group]
            [api.tx :refer [transact!]]))

(defn ->build [ctx]
  (let [group (ctx/->horizontal-group ctx {:pad 2 :space 2})]
    (actor/set-id! group ::action-bar)
    group))

(defn ->button-group [ctx]
  (ctx/->button-group ctx {:max-check-count 1
                           :min-check-count 0}))

(defn- get-action-bar [ctx]
  {:horizontal-group (::action-bar (:game-state.widgets/action-bar-table (ctx/get-stage ctx)))
   :button-group (:action-bar/button-group @(:context/game ctx))})

(defmethod transact! :tx.context.action-bar/add-skill
  [[_ {:keys [property/id property/image] :as skill}] ctx]
  (let [{:keys [horizontal-group button-group]} (get-action-bar ctx)
        button (->image-button ctx image (fn [_]))]
    (actor/set-id! button id)
    (add-tooltip! button #(player-tooltip-text % skill))
    (add-actor! horizontal-group button)
    (add! button-group button)
    ctx))

(defmethod transact! :tx.context.action-bar/remove-skill
  [[_ {:keys [property/id]}] ctx]
  (let [{:keys [horizontal-group button-group]} (get-action-bar ctx)
        button (get horizontal-group id)]
    (remove! button)
    (button-group/remove! button-group button)
    ctx))

(comment

 ;[api.input.keys :as input.keys]
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
             :when (and (key-just-pressed? context (number-str->input-key (name slot)))
                        skill-id)]
       (.setChecked ^Button (.findActor horizontal-group (str skill-id)) true)))

 ; TODO
 ; * cooldown / not usable -> diff. colors ? disable on not able to use skills (stunned?)
 ; * or even sector circling for cooldown like in WoW (clipped !)
 ; * tooltips ! with hotkey-number !
 ;  ( (skills/text skill-id player-entity))
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
