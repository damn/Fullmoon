(ns context.action-bar
  (:require [core.component :as component]
            [api.context :as ctx :refer [->image-button key-just-pressed? ->button-group ->horizontal-group player-tooltip-text]]
            [gdl.scene2d.actor :as actor :refer [remove! add-tooltip!]]
            [gdl.scene2d.group :refer [clear-children! add-actor!]]
            [gdl.scene2d.ui.button-group :refer [clear! add! checked] :as button-group]))

(component/def :context/action-bar {}
  _
  (ctx/create [_ ctx]
    {:horizontal-group (->horizontal-group ctx {:pad 2
                                                :space 2})
     :button-group (->button-group ctx {:max-check-count 1
                                        :min-check-count 0})}))

; (reset-actionbar ctx)

(comment
 (let [stage (api.context/get-stage @gdl.app/current-context)]
   (::action-bar (:gdl.context.ui.actors/main-table stage))
   )
 )

(extend-type api.context.Context
  api.context/Actionbar
  (->action-bar [{{:keys [horizontal-group]} :context/action-bar}]
    horizontal-group)

  (reset-actionbar [{{:keys [horizontal-group button-group]} :context/action-bar}]
    (clear-children! horizontal-group)
    (clear! button-group))

  (selected-skill [{{:keys [button-group]} :context/action-bar}]
    (when-let [skill-button (checked button-group)]
      (actor/id skill-button))))

(defmethod api.context/transact! :tx/actionbar-add-skill
  [[_ {:keys [property/id property/image] :as skill}]
   {{:keys [horizontal-group button-group]} :context/action-bar :as ctx}]
  (let [button (->image-button ctx image (fn [_]))]
    (actor/set-id! button id)
    (add-tooltip! button #(player-tooltip-text % skill))
    (add-actor! horizontal-group button)
    (add! button-group button)
    nil))

(defmethod api.context/transact! :tx/actionbar-remove-skill
  [[_ {:keys [property/id]}]
   {{:keys [horizontal-group button-group]} :context/action-bar}]
  (let [button (get horizontal-group id)]
    (remove! button)
    (button-group/remove! button-group button)
    nil))

(comment

 ;[gdl.input.keys :as input.keys]
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
