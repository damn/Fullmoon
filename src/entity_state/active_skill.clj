(ns entity-state.active-skill
  (:require [utils.core :refer [safe-merge]]
            [core.component :refer [defcomponent]]
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

; TODO safe-merge DRY ?
; filter applicable dry?
; ...?
; => deref source/target always (or check if still valid)
; before each do! ....
; like in tick-system

; TODO can even do ctx/source* and ctx/target* functions or something

; maybe move to transaction handler & call it :tx/with-ctx
; and dissoc-ks (keys of extra-ctx)
; its a more general thing than tx/effect
(defcomponent :tx/effect {}
  (effect/do! [[_ effect-ctx effects] ctx]
    (-> ctx
        (merge effect-ctx)
        (ctx/do! (filter #(effect/applicable? % effect-ctx) effects))
        ; TODO
        ; context/source ?
        ; skill.context ?  ?
        ; generic context ?( projectile hit is not skill context)
        (dissoc :effect/source
                :effect/target
                :effect/direction
                :effect/target-position))))

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
        y (+ (float y) (float (:half-height entity*)))
        center [x (+ y radius)]]
    (g/draw-filled-circle g center radius [1 1 1 0.125])
    (g/draw-sector g center radius
                   90 ; start-angle
                   (* (float action-counter-ratio) 360) ; degree
                   [1 1 1 0.5])
    (g/draw-image g icon [(- (float x) radius) y])))

(defrecord ActiveSkill [eid skill effect-ctx counter]
  state/PlayerState
  (player-enter [_] [[:tx.context.cursor/set :cursors/sandclock]])
  (pause-game? [_] false)
  (manual-tick [_ context])
  (clicked-inventory-cell [_ cell])
  (clicked-skillmenu-skill [_ skill])

  state/State
  (enter [_ ctx]
    [[:tx/sound (:skill/start-action-sound skill)]
     (when (:skill/cooldown skill)
       [:tx.entity/assoc-in eid [:entity/skills (:property/id skill) :skill/cooling-down?] (->counter ctx (:skill/cooldown skill))])
     (when-not (zero? (:skill/cost skill))
       [:tx.entity.stats/pay-mana-cost eid (:skill/cost skill)])])

  (exit [_ _ctx])

  (tick [_ context]
    (cond
     (not (applicable? (safe-merge context effect-ctx) (:skill/effects skill)))
     [[:tx/event eid :action-done]
      ; TODO some sound ?
      ]

     (stopped? context counter)
     [[:tx/event eid :action-done]
      [:tx/effect effect-ctx (:skill/effects skill)]]))

  (render-below [_ entity* g _ctx])
  (render-above [_ entity* g _ctx])
  (render-info [_ entity* g ctx]
    (let [{:keys [property/image skill/effects]} skill]
      (draw-skill-icon g image entity* (:position entity*) (finished-ratio ctx counter))
      (run! #(effect/render-info % g (merge ctx effect-ctx)) effects))))

(defn- apply-action-speed-modifier [entity* skill action-time]
  (/ action-time
     (or (entity/stat entity* (:skill/action-time-modifier-key skill))
         1)))

(defn ->build [context eid [skill effect-ctx]]
  (->ActiveSkill eid
                 skill
                 effect-ctx
                 (->> skill
                      :skill/action-time
                      (apply-action-speed-modifier @eid skill)
                      (->counter context))))
