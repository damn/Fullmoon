(ns core.skill
  (:require [core.ctx :refer :all])
  (:import com.badlogic.gdx.Input$Buttons
           com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup))

(defcomponent :skill/action-time {:data :pos}
  (info-text [[_ v] _ctx]
    (str "[GOLD]Action-Time: " (readable-number v) " seconds[]")))

(defcomponent :skill/cooldown {:data :nat-int}
  (info-text [[_ v] _ctx]
    (when-not (zero? v)
      (str "[SKY]Cooldown: " (readable-number v) " seconds[]"))))

(defcomponent :skill/cost {:data :nat-int}
  (info-text [[_ v] _ctx]
    (when-not (zero? v)
      (str "[CYAN]Cost: " v " Mana[]"))))

(defcomponent :skill/effects
  {:data [:components-ns :effect]})

(defcomponent :skill/start-action-sound {:data :sound})

(defcomponent :skill/action-time-modifier-key
  {:data [:enum [:stats/cast-speed :stats/attack-speed]]}
  (info-text [[_ v] _ctx]
    (str "[VIOLET]" (case v
                      :stats/cast-speed "Spell"
                      :stats/attack-speed "Attack") "[]")))

(def-type :properties/skills
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

(defcomponent :entity/skills
  {:data [:one-to-many :properties/skills]}
  (create [[k skills] eid ctx]
    (cons [:e/assoc eid k nil]
          (for [skill skills]
            [:tx/add-skill eid skill])))

  (info-text [[_ skills] _ctx]
    ; => recursive info-text leads to endless text wall
    #_(when (seq skills)
        (str "[VIOLET]Skills: " (str/join "," (map name (keys skills))) "[]")))

  (tick [[k skills] eid ctx]
    (for [{:keys [skill/cooling-down?] :as skill} (vals skills)
          :when (and cooling-down?
                     (stopped? ctx cooling-down?))]
      [:e/assoc-in eid [k (:property/id skill) :skill/cooling-down?] false])))

(defn has-skill? [{:keys [entity/skills]} {:keys [property/id]}]
  (contains? skills id))

(defcomponent :tx/add-skill
  (do! [[_ entity {:keys [property/id] :as skill}] _ctx]
    (assert (not (has-skill? @entity skill)))
    [[:e/assoc-in entity [:entity/skills id] skill]
     (when (:entity/player? @entity)
       [:tx.action-bar/add skill])]))

(defcomponent :tx/remove-skill
  (do! [[_ entity {:keys [property/id] :as skill}] _ctx]
    (assert (has-skill? @entity skill))
    [[:e/dissoc-in entity [:entity/skills id]]
     (when (:entity/player? @entity)
       [:tx.action-bar/remove skill])]))

(defsystem render-effect "Renders effect during active-skill state while active till done?. Default do nothing." [_ g ctx])
(defmethod render-effect :default [_ g ctx])

(defcomponent :entity-effects {:data [:components-ns :effect.entity]})

; TODO applicable targets? e.g. projectiles/effect s/???item entiteis ??? check
; same code as in render entities on world view screens/world
(defn- creatures-in-los-of-player [ctx]
  (->> (active-entities ctx)
       (filter #(:creature/species @%))
       (filter #(line-of-sight? ctx (player-entity* ctx) @%))
       (remove #(:entity/player? @%))))

; TODO targets projectiles with -50% hp !!

(comment
 ; TODO showing one a bit further up
 ; maybe world view port is cut
 ; not quite showing correctly.
 (let [ctx @app/state
       targets (creatures-in-los-of-player ctx)]
   (count targets)
   #_(sort-by #(% 1) (map #(vector (:entity.creature/name @%)
                                   (:position @%)) targets)))

 )

(defcomponent :effect/target-all
  {:data [:map [:entity-effects]]
   :let {:keys [entity-effects]}}
  (info-text [_ ctx]
    "[LIGHT_GRAY]All visible targets[]")

  (applicable? [_ _ctx]
    true)

  (useful? [_ _ctx]
    ; TODO
    false
    )

  (do! [_ {:keys [effect/source] :as ctx}]
    (let [source* @source]
      (apply concat
             (for [target (creatures-in-los-of-player ctx)]
               [[:tx/line-render {:start (:position source*) #_(start-point source* target*)
                                  :end (:position @target)
                                  :duration 0.05
                                  :color [1 0 0 0.75]
                                  :thick? true}]
                ; some sound .... or repeat smae sound???
                ; skill do sound  / skill start sound >?
                ; problem : nested tx/effect , we are still having direction/target-position
                ; at sub-effects
                ; and no more safe - merge
                ; find a way to pass ctx / effect-ctx separate ?
                [:tx/effect {:effect/source source :effect/target target} entity-effects]]))))

  (render-effect [_ g {:keys [effect/source] :as ctx}]
    (let [source* @source]
      (doseq [target* (map deref (creatures-in-los-of-player ctx))]
        (draw-line g
                   (:position source*) #_(start-point source* target*)
                   (:position target*)
                   [1 0 0 0.5])))))

(defn- in-range? [entity* target* maxrange] ; == circle-collides?
  (< (- (float (v-distance (:position entity*)
                           (:position target*)))
        (float (:radius entity*))
        (float (:radius target*)))
     (float maxrange)))

; TODO use at projectile & also adjust rotation
(defn- start-point [entity* target*]
  (v-add (:position entity*)
         (v-scale (direction entity* target*)
                  (:radius entity*))))

(defn- end-point [entity* target* maxrange]
  (v-add (start-point entity* target*)
         (v-scale (direction entity* target*)
                  maxrange)))

(defcomponent :maxrange {:data :pos}
  (info-text [[_ maxrange] _ctx]
    (str "[LIGHT_GRAY]Range " maxrange " meters[]")))

(defcomponent :effect/target-entity
  {:let {:keys [maxrange entity-effects]}
   :data [:map [:entity-effects :maxrange]]
   :editor/doc "Applies entity-effects to a target if they are inside max-range & in line of sight.
               Cancels if line of sight is lost. Draws a red/yellow line wheter the target is inside the max range. If the effect is to be done and target out of range -> draws a hit-ground-effect on the max location."}

  (applicable? [_ {:keys [effect/target] :as ctx}]
    (and target
         (some #(applicable? % ctx) entity-effects)))

  (useful? [_ {:keys [effect/source effect/target]}]
    (assert source)
    (assert target)
    (in-range? @source @target maxrange))

  (do! [_ {:keys [effect/source effect/target]}]
    (let [source* @source
          target* @target]
      (if (in-range? source* target* maxrange)
        (cons
         [:tx/line-render {:start (start-point source* target*)
                           :end (:position target*)
                           :duration 0.05
                           :color [1 0 0 0.75]
                           :thick? true}]
         ; TODO => make new context with end-point ... and check on point entity
         ; friendly fire ?!
         ; player maybe just direction possible ?!
         entity-effects)
        [; TODO
         ; * clicking on far away monster
         ; * hitting ground in front of you ( there is another monster )
         ; * -> it doesn't get hit ! hmmm
         ; * either use 'MISS' or get enemy entities at end-point
         [:tx/audiovisual (end-point source* target* maxrange) :audiovisuals/hit-ground]])))

  (render-effect [_ g {:keys [effect/source effect/target]}]
    (let [source* @source
          target* @target]
      (draw-line g
                 (start-point source* target*)
                 (end-point   source* target* maxrange)
                 (if (in-range? source* target* maxrange)
                   [1 0 0 0.5]
                   [1 1 0 0.5])))))

; would have to do this only if effect even needs target ... ?
(defn- check-remove-target [{:keys [effect/source] :as ctx}]
  (update ctx :effect/target (fn [target]
                               (when (and target
                                          (not (:entity/destroyed? @target))
                                          (line-of-sight? ctx @source @target))
                                 target))))

(defn- effect-applicable? [ctx effects]
  (let [ctx (check-remove-target ctx)]
    (some #(applicable? % ctx) effects)))

(defn- mana-value [entity*]
  (if-let [mana (entity-stat entity* :stats/mana)]
    (mana 0)
    0))

(defn- not-enough-mana? [entity* {:keys [skill/cost]}]
  (> cost (mana-value entity*)))

(defn- skill-usable-state
  [ctx entity* {:keys [skill/cooling-down? skill/effects] :as skill}]
  (cond
   cooling-down?
   :cooldown

   (not-enough-mana? entity* skill)
   :not-enough-mana

   (not (effect-applicable? ctx effects))
   :invalid-params

   :else
   :usable))

; SCHEMA effect-ctx
; * source = always available
; # npc:
;   * target = maybe
;   * direction = maybe
; # player
;  * target = maybe
;  * target-position  = always available (mouse world position)
;  * direction  = always available (from mouse world position)

(defn- nearest-enemy [{:keys [context/grid]} entity*]
  (nearest-entity @(grid (entity-tile entity*))
                  (enemy-faction entity*)))

(defn- ->npc-effect-ctx [ctx entity*]
  (let [target (nearest-enemy ctx entity*)
        target (when (and target (line-of-sight? ctx entity* @target))
                 target)]
    {:effect/source (:entity/id entity*)
     :effect/target target
     :effect/direction (when target (direction entity* @target))}))

(defn- ->player-effect-ctx [ctx entity*]
  (let [target* (mouseover-entity* ctx)
        target-position (or (and target* (:position target*))
                            (world-mouse-position ctx))]
    {:effect/source (:entity/id entity*)
     :effect/target (:entity/id target*)
     :effect/target-position target-position
     :effect/direction (v-direction (:position entity*) target-position)}))

(defcomponent :tx/effect
  (do! [[_ effect-ctx effects] ctx]
    (-> ctx
        (merge effect-ctx)
        (effect! (filter #(applicable? % effect-ctx) effects))
        ; TODO
        ; context/source ?
        ; skill.context ?  ?
        ; generic context ?( projectile hit is not skill context)
        (dissoc :effect/source
                :effect/target
                :effect/direction
                :effect/target-position))))

(defn- draw-skill-icon [g icon entity* [x y] action-counter-ratio]
  (let [[width height] (:world-unit-dimensions icon)
        _ (assert (= width height))
        radius (/ (float width) 2)
        y (+ (float y) (float (:half-height entity*)) (float 0.15))
        center [x (+ y radius)]]
    (draw-filled-circle g center radius [1 1 1 0.125])
    (draw-sector g center radius
                 90 ; start-angle
                 (* (float action-counter-ratio) 360) ; degree
                 [1 1 1 0.5])
    (draw-image g icon [(- (float x) radius) y])))

(defn- apply-action-speed-modifier [entity* skill action-time]
  (/ action-time
     (or (entity-stat entity* (:skill/action-time-modifier-key skill))
         1)))

(defcomponent :active-skill
  {:let {:keys [eid skill effect-ctx counter]}}
  (->mk [[_ eid [skill effect-ctx]] ctx]
    {:eid eid
     :skill skill
     :effect-ctx effect-ctx
     :counter (->> skill
                   :skill/action-time
                   (apply-action-speed-modifier @eid skill)
                   (->counter ctx))})

  (player-enter [_]
    [[:tx/cursor :cursors/sandclock]])

  (pause-game? [_]
    false)

  (enter [_ ctx]
    [[:tx/sound (:skill/start-action-sound skill)]
     (when (:skill/cooldown skill)
       [:e/assoc-in eid [:entity/skills (:property/id skill) :skill/cooling-down?] (->counter ctx (:skill/cooldown skill))])
     (when-not (zero? (:skill/cost skill))
       [:tx.entity.stats/pay-mana-cost eid (:skill/cost skill)])])

  (tick [_ eid context]
    (cond
     (not (effect-applicable? (safe-merge context effect-ctx) (:skill/effects skill)))
     [[:tx/event eid :action-done]
      ; TODO some sound ?
      ]

     (stopped? context counter)
     [[:tx/event eid :action-done]
      [:tx/effect effect-ctx (:skill/effects skill)]]))

  (render-info [_ entity* g ctx]
    (let [{:keys [entity/image skill/effects]} skill]
      (draw-skill-icon g image entity* (:position entity*) (finished-ratio ctx counter))
      (run! #(render-effect % g (merge ctx effect-ctx)) effects))))

; TODO
; split it into 3 parts
; applicable
; useful
; usable?
(defn- effect-useful? [ctx effects]
  ;(println "Check useful? for effects: " (map first effects))
  (let [applicable-effects (filter #(applicable? % ctx) effects)
        ;_ (println "applicable-effects: " (map first applicable-effects))
        useful-effect (some #(useful? % ctx) applicable-effects)]
    ;(println "Useful: " useful-effect)
    useful-effect))

(defn- npc-choose-skill [ctx entity*]
  (->> entity*
       :entity/skills
       vals
       (sort-by #(or (:skill/cost %) 0))
       reverse
       (filter #(and (= :usable (skill-usable-state ctx entity* %))
                     (effect-useful? ctx (:skill/effects %))))
       first))

(comment
 (let [uid 76
       ctx @app/state
       entity* @(get-entity ctx uid)
       effect-ctx (->npc-effect-ctx ctx entity*)]
   (npc-choose-skill (safe-merge ctx effect-ctx) entity*))
 )

(defcomponent :npc-idle
  {:let {:keys [eid]}}
  (->mk [[_ eid] _ctx]
    {:eid eid})

  (tick [_ eid ctx]
    (let [entity* @eid
          effect-ctx (->npc-effect-ctx ctx entity*)]
      (if-let [skill (npc-choose-skill (safe-merge ctx effect-ctx) entity*)]
        [[:tx/event eid :start-action [skill effect-ctx]]]
        [[:tx/event eid :movement-direction (potential-fields-follow-to-enemy ctx eid)]]))))

(defn- selected-skill [ctx]
  (let [button-group (:action-bar (:context/widgets ctx))]
    (when-let [skill-button (.getChecked ^ButtonGroup button-group)]
      (actor-id skill-button))))

(defn- inventory-window [ctx]
  (get (:windows (stage-get ctx)) :inventory-window))

(defn- denied [text]
  [[:tx/sound "sounds/bfxr_denied.wav"]
   [:tx/msg-to-player text]])

(defmulti ^:private on-clicked
  (fn [_context entity*]
    (:type (:entity/clickable entity*))))

(defmethod on-clicked :clickable/item
  [context clicked-entity*]
  (let [player-entity* (player-entity* context)
        item (:entity/item clicked-entity*)
        clicked-entity (:entity/id clicked-entity*)]
    (cond
     (visible? (inventory-window context))
     [[:tx/sound "sounds/bfxr_takeit.wav"]
      [:e/destroy clicked-entity]
      [:tx/event (:entity/id player-entity*) :pickup-item item]]

     (can-pickup-item? player-entity* item)
     [[:tx/sound "sounds/bfxr_pickup.wav"]
      [:e/destroy clicked-entity]
      [:tx/pickup-item (:entity/id player-entity*) item]]

     :else
     [[:tx/sound "sounds/bfxr_denied.wav"]
      [:tx/msg-to-player "Your Inventory is full"]])))

(defmethod on-clicked :clickable/player
  [ctx _clicked-entity*]
  (toggle-visible! (inventory-window ctx))) ; TODO no tx

(defn- clickable->cursor [mouseover-entity* too-far-away?]
  (case (:type (:entity/clickable mouseover-entity*))
    :clickable/item (if too-far-away?
                      :cursors/hand-before-grab-gray
                      :cursors/hand-before-grab)
    :clickable/player :cursors/bag))

(defn- ->clickable-mouseover-entity-interaction [ctx player-entity* mouseover-entity*]
  (if (< (v-distance (:position player-entity*) (:position mouseover-entity*))
         (:entity/click-distance-tiles player-entity*))
    [(clickable->cursor mouseover-entity* false) (fn [] (on-clicked ctx mouseover-entity*))]
    [(clickable->cursor mouseover-entity* true)  (fn [] (denied "Too far away"))]))

; TODO move to inventory-window extend Context
(defn- inventory-cell-with-item? [ctx actor]
  (and (parent actor)
       (= "inventory-cell" (actor-name (parent actor)))
       (get-in (:entity/inventory (player-entity* ctx))
               (actor-id (parent actor)))))

(defn- mouseover-actor->cursor [ctx]
  (let [actor (mouse-on-actor? ctx)]
    (cond
     (inventory-cell-with-item? ctx actor) :cursors/hand-before-grab
     (window-title-bar? actor) :cursors/move-window
     (button? actor) :cursors/over-button
     :else :cursors/default)))

(defn- ->interaction-state [context entity*]
  (let [mouseover-entity* (mouseover-entity* context)]
    (cond
     (mouse-on-actor? context)
     [(mouseover-actor->cursor context)
      (fn []
        nil)] ; handled by actors themself, they check player state

     (and mouseover-entity*
          (:entity/clickable mouseover-entity*))
     (->clickable-mouseover-entity-interaction context entity* mouseover-entity*)

     :else
     (if-let [skill-id (selected-skill context)]
       (let [skill (skill-id (:entity/skills entity*))
             effect-ctx (->player-effect-ctx context entity*)
             state (skill-usable-state (safe-merge context effect-ctx) entity* skill)]
         (if (= state :usable)
           (do
            ; TODO cursor AS OF SKILL effect (SWORD !) / show already what the effect would do ? e.g. if it would kill highlight
            ; different color ?
            ; => e.g. meditation no TARGET .. etc.
            [:cursors/use-skill
             (fn []
               [[:tx/event (:entity/id entity*) :start-action [skill effect-ctx]]])])
           (do
            ; TODO cursor as of usable state
            ; cooldown -> sanduhr kleine
            ; not-enough-mana x mit kreis?
            ; invalid-params -> depends on params ...
            [:cursors/skill-not-usable
             (fn []
               (denied (case state
                         :cooldown "Skill is still on cooldown"
                         :not-enough-mana "Not enough mana"
                         :invalid-params "Cannot use this here")))])))
       [:cursors/no-skill-selected
        (fn [] (denied "No selected skill"))]))))

(defcomponent :player-idle
  {:let {:keys [eid]}}
  (->mk [[_ eid] _ctx]
    {:eid eid})

  (pause-game? [_]
    true)

  (manual-tick [_ ctx]
    (if-let [movement-vector (WASD-movement-vector)]
      [[:tx/event eid :movement-input movement-vector]]
      (let [[cursor on-click] (->interaction-state ctx @eid)]
        (cons [:tx/cursor cursor]
              (when (.isButtonJustPressed gdx-input Input$Buttons/LEFT)
                (on-click))))))

  (clicked-inventory-cell [_ cell]
    ; TODO no else case
    (when-let [item (get-in (:entity/inventory @eid) cell)]
      [[:tx/sound "sounds/bfxr_takeit.wav"]
       [:tx/event eid :pickup-item item]
       [:tx/remove-item eid cell]]))

  (clicked-skillmenu-skill [_ skill]
    (let [free-skill-points (:entity/free-skill-points @eid)]
      ; TODO no else case, no visible free-skill-points
      (when (and (pos? free-skill-points)
                 (not (has-skill? @eid skill)))
        [[:e/assoc eid :entity/free-skill-points (dec free-skill-points)]
         [:tx/add-skill eid skill]]))))
