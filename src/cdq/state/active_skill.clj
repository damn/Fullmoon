(ns cdq.state.active-skill
  (:require [gdl.graphics :as g]
            [data.val-max :refer [apply-val]]
            [cdq.api.context :refer [valid-params? effect-render-info stopped? finished-ratio ->counter]]
            [cdq.api.entity :as entity]
            [cdq.api.state :as state]))

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

(defn- set-skill-to-cooldown [entity* {:keys [property/id skill/cooldown] :as skill} ctx]
  (when cooldown
    [:tx/assoc-in (:entity/id entity*) [:entity/skills id :skill/cooling-down?] (->counter ctx cooldown)]))

(defn- pay-skill-mana-cost [{:keys [entity/id entity/mana]} {:keys [skill/cost]}]
  (when cost
    [:tx/assoc id :entity/mana (apply-val mana #(- % cost))]))

(defrecord ActiveSkill [skill effect-context counter]
  state/PlayerState
  (player-enter [_] [[:tx/cursor :cursors/sandclock]])
  (pause-game? [_] false)
  (manual-tick [_ entity* context])
  (clicked-inventory-cell [_ entity* cell])
  (clicked-skillmenu-skill [_ entity* skill])

  state/State
  (enter [_ entity* ctx]
    [[:tx/sound (:skill/start-action-sound skill)]
     (set-skill-to-cooldown entity* skill ctx)
     (pay-skill-mana-cost entity* skill)])

  (exit [_ entity* _ctx])

  (tick [_ {:keys [entity/id]} context]
    (let [effect (:skill/effect skill)
          effect-context (merge context effect-context)]
      (cond
       (not (valid-params? effect-context effect))
       [[:tx/event id :action-done]]

       (stopped? context counter)
       [[:tx/effect effect-context effect]
        [:tx/event id :action-done]])))

  (render-below [_ entity* g _ctx])
  (render-above [_ entity* g _ctx])
  (render-info [_ {:keys [entity/position] :as entity*} g ctx]
    (let [{:keys [property/image skill/effect]} skill]
      (draw-skill-icon g image entity* position (finished-ratio ctx counter))
      (effect-render-info (merge ctx effect-context) g effect))))

(defn- apply-action-speed-modifier [entity* skill action-time]
  (/ action-time
     (or (get (:entity/stats entity*)
              (:skill/action-time-modifier-key skill))
         1)))

(defn ->CreateWithCounter [context entity* [skill effect-context]]
  ; assert keys effect-context only with 'effect/'
  ; so we don't use an outdated 'context' in the State update
  ; when we call State protocol functions we call it with the current context
  (assert (every? #(= "effect" (namespace %)) (keys effect-context)))
  (->ActiveSkill skill
                 effect-context
                 (->> skill
                      :skill/action-time
                      (apply-action-speed-modifier entity* skill)
                      (->counter context))))
