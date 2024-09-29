(in-ns 'core.app)

(defn- render-infostr-on-bar [g infostr x y h]
  (draw-text g {:text infostr
                :x (+ x 75)
                :y (+ y 2)
                :up? true}))

(defn- ->hp-mana-bars [context]
  (let [rahmen      (->image context "images/rahmen.png")
        hpcontent   (->image context "images/hp.png")
        manacontent (->image context "images/mana.png")
        x (/ (gui-viewport-width context) 2)
        [rahmenw rahmenh] (:pixel-dimensions rahmen)
        y-mana 80 ; action-bar-icon-size
        y-hp (+ y-mana rahmenh)
        render-hpmana-bar (fn [g ctx x y contentimg minmaxval name]
                            (draw-image g rahmen [x y])
                            (draw-image g
                                        (sub-image ctx contentimg [0 0 (* rahmenw (val-max-ratio minmaxval)) rahmenh])
                                        [x y])
                            (render-infostr-on-bar g (str (readable-number (minmaxval 0)) "/" (minmaxval 1) " " name) x y rahmenh))]
    (->actor {:draw (fn [g ctx]
                         (let [player-entity* (player-entity* ctx)
                               x (- x (/ rahmenw 2))]
                           (render-hpmana-bar g ctx x y-hp   hpcontent   (entity-stat player-entity* :stats/hp) "HP")
                           (render-hpmana-bar g ctx x y-mana manacontent (entity-stat player-entity* :stats/mana) "MP")))})))

(defn- skill-info [{:keys [entity/skills]}]
  (clojure.string/join "\n"
                       (for [{:keys [property/id skill/cooling-down?]} (vals skills)
                             :when cooling-down? ]
                         [id [:cooling-down? (boolean cooling-down?)]])))

; TODO component to info-text move to the component itself.....
(defn- debug-infos ^String [ctx]
  (let [world-mouse (world-mouse-position ctx)]
    (str
     "logic-frame: " (logic-frame ctx) "\n"
     "FPS: " (frames-per-second)  "\n"
     "Zoom: " (zoom (world-camera ctx)) "\n"
     "World: "(mapv int world-mouse) "\n"
     "X:" (world-mouse 0) "\n"
     "Y:" (world-mouse 1) "\n"
     "GUI: " (gui-mouse-position ctx) "\n"
     "paused? " (:context/paused? ctx) "\n"
     "elapsed-time " (readable-number (elapsed-time ctx)) " seconds \n"
     (skill-info (player-entity* ctx))
     (when-let [entity* (mouseover-entity* ctx)]
       (str "Mouseover-entity uid: " (:entity/uid entity*)))
     ;"\nMouseover-Actor:\n"
     #_(when-let [actor (mouse-on-actor? ctx)]
         (str "TRUE - name:" (.getName actor)
              "id: " (actor-id actor)
              )))))

(defn- ->debug-window [context]
  (let [label (->label "")
        window (->window {:title "Debug"
                          :id :debug-window
                          :visible? false
                          :position [0 (gui-viewport-height context)]
                          :rows [[label]]})]
    (add-actor! window (->actor {:act #(do
                                        (.setText label (debug-infos %))
                                        (.pack window))}))
    window))

(def ^:private disallowed-keys [:entity/skills
                                :entity/state
                                :entity/faction
                                :active-skill])

(defn- ->entity-info-window [context]
  (let [label (->label "")
        window (->window {:title "Info"
                          :id :entity-info-window
                          :visible? false
                          :position [(gui-viewport-width context) 0]
                          :rows [[{:actor label :expand? true}]]})]
    ; TODO do not change window size ... -> no need to invalidate layout, set the whole stage up again
    ; => fix size somehow.
    (add-actor! window (->actor {:act (fn update-label-text [ctx]
                                        ; items then have 2x pretty-name
                                        #_(.setText (.getTitleLabel window)
                                                    (if-let [entity* (mouseover-entity* ctx)]
                                                      (info-text [:property/pretty-name (:property/pretty-name entity*)])
                                                      "Entity Info"))
                                        (.setText label
                                                  (str (when-let [entity* (mouseover-entity* ctx)]
                                                         (->info-text
                                                          ; don't use select-keys as it loses Entity record type
                                                          (apply dissoc entity* disallowed-keys)
                                                          ctx))))
                                        (.pack window))}))
    window))

(def ^:private image-scale 2)

(defn- ->action-bar []
  (let [group (->horizontal-group {:pad 2 :space 2})]
    (set-id! group ::action-bar)
    group))

(defn- ->action-bar-button-group []
  (->button-group {:max-check-count 1 :min-check-count 0}))

(defn- get-action-bar [ctx]
  {:horizontal-group (::action-bar (:action-bar-table (stage-get ctx)))
   :button-group (:action-bar (:context/widgets ctx))})

(defcomponent :tx.action-bar/add
  (do! [[_ {:keys [property/id entity/image] :as skill}] ctx]
    (let [{:keys [horizontal-group button-group]} (get-action-bar ctx)
          button (->image-button image identity {:scale image-scale})]
      (set-id! button id)
      (add-tooltip! button #(->info-text skill (assoc % :effect/source (player-entity %))))
      (add-actor! horizontal-group button)
      (bg-add! button-group button)
      ctx)))

(defcomponent :tx.action-bar/remove
  (do! [[_ {:keys [property/id]}] ctx]
    (let [{:keys [horizontal-group button-group]} (get-action-bar ctx)
          button (get horizontal-group id)]
      (remove! button)
      (bg-remove! button-group button)
      ctx)))

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

(def ^:private ctx-msg-player :context/msg-to-player)

(def ^:private duration-seconds 1.5)

(defn- draw-player-message [g ctx]
  (when-let [{:keys [message]} (ctx-msg-player ctx)]
    (draw-text g {:x (/ (gui-viewport-width ctx) 2)
                  :y (+ (/ (gui-viewport-height ctx) 2) 200)
                  :text message
                  :scale 2.5
                  :up? true})))

(defn- check-remove-message [ctx]
  (when-let [{:keys [counter]} (ctx-msg-player ctx)]
    (swap! app-state update ctx-msg-player update :counter + (delta-time))
    (when (>= counter duration-seconds)
      (swap! app-state assoc ctx-msg-player nil))))

(defcomponent :tx/msg-to-player
  (do! [[_ message] ctx]
    (assoc ctx :context/msg-to-player {:message message :counter 0})))

(defcomponent :widgets/player-message
  (->mk [_ _ctx]
    (->actor {:draw draw-player-message
              :act check-remove-message})))

(defn- ->ui-actors [ctx widget-data]
  [(->table {:rows [[{:actor (->action-bar)
                      :expand? true
                      :bottom? true}]]
             :id :action-bar-table
             :cell-defaults {:pad 2}
             :fill-parent? true})
   (->hp-mana-bars ctx)
   (->group {:id :windows
             :actors [(->debug-window ctx)
                      (->entity-info-window ctx)
                      (->inventory-window ctx widget-data)]})
   (->actor {:draw draw-item-on-cursor})
   (->mk [:widgets/player-message] ctx)])

(defcomponent :context/widgets
  (->mk [_ ctx]
    (let [widget-data {:action-bar (->action-bar-button-group)
                       :slot->background (->inventory-window-data ctx)}
          stage (stage-get ctx)]
      (s-clear! stage)
      (run! #(s-add! stage %) (->ui-actors ctx widget-data))
      widget-data)))
