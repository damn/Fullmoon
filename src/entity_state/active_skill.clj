(ns entity-state.active-skill
  (:require [data.val-max :refer [apply-val]]
            [core.effect-txs :as effect-txs]
            [api.context :refer [stopped? finished-ratio ->counter]]
            [api.entity :as entity]
            [api.entity-state :as state]
            [api.graphics :as g] ))

(defn skill-usable-state [effect-txs
                          {:keys [entity/mana]}
                          {:keys [skill/cost skill/cooling-down? skill/effect]}]
  (cond
   cooling-down?                               :cooldown
   (and cost (> cost (mana 0)))                :not-enough-mana
   (not (effect-txs/valid-params? effect-txs)) :invalid-params
   :else                                       :usable))

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
    [:tx.entity/assoc-in (:entity/id entity*) [:entity/skills id :skill/cooling-down?] (->counter ctx cooldown)]))

(defn- pay-skill-mana-cost [{:keys [entity/id entity/mana]} {:keys [skill/cost]}]
  (when cost
    [:tx.entity/assoc id :entity/mana (apply-val mana #(- % cost))]))

(defrecord ActiveSkill [skill effect-txs counter]
  state/PlayerState
  (player-enter [_] [[:tx.context.cursor/set :cursors/sandclock]])
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
    (cond
     (not (effect-txs/valid-params? effect-txs))
     [[:tx/event id :action-done]]

     (stopped? context counter)
     (cons [:tx/event id :action-done]
           effect-txs)))

  (render-below [_ entity* g _ctx])
  (render-above [_ entity* g _ctx])
  (render-info [_ {:keys [entity/position] :as entity*} g ctx]
    (let [{:keys [property/image skill/effect]} skill]
      (draw-skill-icon g image entity* position (finished-ratio ctx counter))
      (effect-txs/render-info g effect-txs))))

(defn- apply-action-speed-modifier [entity* skill action-time]
  (/ action-time
     (or (get (:entity/stats entity*)
              (:skill/action-time-modifier-key skill))
         1)))

(defn ->CreateWithCounter [context entity* [skill effect-txs]]
  (->ActiveSkill skill
                 effect-txs
                 (->> skill
                      :skill/action-time
                      (apply-action-speed-modifier entity* skill)
                      (->counter context))))
