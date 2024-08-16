(ns entity-state.active-skill
  (:require [api.context :as ctx :refer [stopped? finished-ratio ->counter]]
            [api.effect :as effect]
            [api.entity :as entity]
            [api.entity-state :as state]
            [api.graphics :as g]
            [api.tx :refer [transact!]]))

; SCHEMA effect-ctx
; * source = always available
; # npc:
;   * target = maybe
;   * direction = maybe
; # player
;  * target = maybe
;  * target-position  = always available
;  * direction  = always available

; TODO
; also target not destroyed
; line of sight part of all target fof ?
; source anyway available no need check  ?
; damage -> hp ...
; move armor out of damage fofo
; stun needs creature state ..

(defmethod transact! :tx/effect [[_ effect-ctx effect] ctx]
  (-> ctx
      (merge effect-ctx)
      (ctx/transact-all! effect)
      (dissoc :effect/source
              :effect/target
              :effect/direction
              :effect/target-position)))

; would have to do this only if effect even needs target ... ?
(defn- check-remove-target [{:keys [effect/source] :as effect-ctx} ctx]
  (update effect-ctx :effect/target
          (fn [target]
            (when (and target
                       (not (:entity/destroyed? @target))
                       (ctx/line-of-sight? ctx @source @target))
              target))))

(defn- usable? [{:keys [effect/source effect/target] :as effect-ctx}
                      effect
                      ctx]
  (let [effect-ctx (check-remove-target effect-ctx ctx)]
    (every? #(effect/usable? % effect-ctx) effect)))

(defn- not-enough-mana? [entity* {:keys [skill/cost]}]
  (> cost ((entity/stat entity* :stats/mana) 0)))

(defn skill-usable-state [effect-ctx
                          entity*
                          {:keys [skill/cooling-down? skill/effect] :as skill}
                          ctx]
  (cond
   cooling-down?
   :cooldown

   (not-enough-mana? entity* skill)
   :not-enough-mana

   (not (usable? effect-ctx effect ctx))
   :invalid-params

   :else
   :usable))

(defn- draw-skill-icon [g icon entity* [x y] action-counter-ratio]
  (let [[width height] (:world-unit-dimensions icon)
        _ (assert (= width height))
        radius (/ (float width) 2)
        y (+ (float y) (float (:half-height (:entity/body entity*))))
        center [x (+ y radius)]]
    (g/draw-filled-circle g center radius [1 1 1 0.125])
    (g/draw-sector g center radius
                   90 ; start-angle
                   (* (float action-counter-ratio) 360) ; degree
                   [1 1 1 0.5])
    (g/draw-image g icon [(- (float x) radius) y])))

(defrecord ActiveSkill [skill effect-ctx counter]
  state/PlayerState
  (player-enter [_] [[:tx.context.cursor/set :cursors/sandclock]])
  (pause-game? [_] false)
  (manual-tick [_ entity* context])
  (clicked-inventory-cell [_ entity* cell])
  (clicked-skillmenu-skill [_ entity* skill])

  state/State
  (enter [_ {:keys [entity/id]} ctx]
    [[:tx/sound (:skill/start-action-sound skill)]
     (when (:skill/cooldown skill)
       [:tx.entity/assoc-in id [:entity/skills (:property/id skill) :skill/cooling-down?] (->counter ctx (:skill/cooldown skill))])
     (when-not (zero? (:skill/cost skill))
       [:tx.entity.stats/pay-mana-cost id (:skill/cost skill)])])

  (exit [_ entity* _ctx])

  (tick [_ {:keys [entity/id]} context]
    (cond
     (not (usable? effect-ctx (:skill/effect skill) context))
     [[:tx/event id :action-done]
      ; TODO some sound ?
      ]

     (stopped? context counter)
     [[:tx/event id :action-done]
      [:tx/effect effect-ctx (:skill/effect skill)]]))

  (render-below [_ entity* g _ctx])
  (render-above [_ entity* g _ctx])
  (render-info [_ entity* g ctx]
    (let [{:keys [property/image skill/effect]} skill]
      (draw-skill-icon g image entity* (entity/position entity*) (finished-ratio ctx counter))
      (run! #(effect/render-info % effect-ctx g) effect))))

(defn- apply-action-speed-modifier [entity* skill action-time]
  (/ action-time
     (or (entity/stat entity* (:skill/action-time-modifier-key skill))
         1)))

(defn ->CreateWithCounter [context entity* [skill effect-ctx]]
  (->ActiveSkill skill
                 effect-ctx
                 (->> skill
                      :skill/action-time
                      (apply-action-speed-modifier entity* skill)
                      (->counter context))))
