(ns entity-state.active-skill
  (:require [utils.core :refer [safe-merge]]
            [api.context :as ctx :refer [stopped? finished-ratio ->counter]]
            [api.effect :as effect]
            [api.entity :as entity]
            [api.entity-state :as state]
            [api.graphics :as g]))

; SCHEMA effect-ctx
; * source = always available
; # npc:
;   * target = maybe
;   * direction = maybe
; # player
;  * target = maybe
;  * target-position  = always available
;  * direction  = always available

; maybe move to transaction handler & call it :tx/with-ctx
; and dissoc-ks (keys of extra-ctx)
; its a more general thing than tx/effect
(defmethod effect/do! :tx/effect [[_ effect-ctx effects] ctx]
  (-> ctx
      (safe-merge effect-ctx)
      (ctx/do! (filter #(effect/applicable? % effect-ctx) effects))
      ; TODO
      ; context/source ?
      ; skill.context ?  ?
      ; generic context ?( projectile hit is not skill context)
      (dissoc :effect/source
              :effect/target
              :effect/direction
              :effect/target-position)))

; would have to do this only if effect even needs target ... ?
(defn- check-remove-target [{:keys [effect/source] :as ctx}]
  (update ctx :effect/target (fn [target]
                               (when (and target
                                          (not (:entity/destroyed? @target))
                                          (ctx/line-of-sight? ctx @source @target))
                                 target))))

(defn- applicable? [{:keys [effect/source effect/target] :as ctx} effects]
  (-> ctx
      check-remove-target
      (ctx/effect-applicable? effects)))

(defn- mana-value [entity*]
  (if-let [mana (entity/stat entity* :stats/mana)]
    (mana 0)
    0))

(defn- not-enough-mana? [entity* {:keys [skill/cost]}]
  (> cost (mana-value entity*)))

(defn skill-usable-state [ctx
                          entity*
                          {:keys [skill/cooling-down? skill/effects] :as skill}]
  (cond
   cooling-down?
   :cooldown

   (not-enough-mana? entity* skill)
   :not-enough-mana

   (not (applicable? ctx effects))
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
     (not (applicable? (safe-merge context effect-ctx) (:skill/effects skill)))
     [[:tx/event id :action-done]
      ; TODO some sound ?
      ]

     (stopped? context counter)
     [[:tx/event id :action-done]
      [:tx/effect effect-ctx (:skill/effects skill)]]))

  (render-below [_ entity* g _ctx])
  (render-above [_ entity* g _ctx])
  (render-info [_ entity* g ctx]
    (let [{:keys [property/image skill/effects]} skill]
      (draw-skill-icon g image entity* (entity/position entity*) (finished-ratio ctx counter))
      (run! #(effect/render-info % g effect-ctx) effects))))

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
