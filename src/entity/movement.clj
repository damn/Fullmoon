(ns entity.movement
  (:require [core.component :refer [defcomponent]]
            [math.vector :as v]
            [api.entity :as entity]
            [api.context :refer [world-grid]]
            [entity.body :as body]
            [api.world.grid :refer [valid-position?]]
            [core.data :as data]))

(def max-delta-time 0.04)

; set max speed so small entities are not skipped by projectiles
; could set faster than max-speed if I just do multiple smaller movement steps in one frame
(def ^:private max-speed (/ body/min-solid-body-size max-delta-time))

; for adding speed multiplier modifier -> need to take max-speed into account!
(defn- update-position [entity* delta direction-vector]
  (let [speed (:entity/movement entity*)
        apply-delta (fn [position]
                      (mapv #(+ %1 (* %2 speed delta)) position direction-vector))]
    (-> entity*
        (update :entity/position apply-delta)
        (update-in [:entity/body :left-bottom] apply-delta))))

(defn- update-position-non-solid [ctx entity* direction]
  (update-position entity* (ctx/delta-time ctx) direction))

(defn- try-move [ctx entity* direction]
  (let [entity* (update-position entity* (ctx/delta-time ctx) direction)]
    (when (valid-position? (world-grid ctx) entity*) ; TODO call on ctx shortcut fn
      entity*)))

; TODO sliding threshold
; TODO name - with-sliding? 'on'
(defn- update-position-solid [ctx entity* {vx 0 vy 1 :as direction}]
  (let [xdir (Math/signum (float vx))
        ydir (Math/signum (float vy))]
    (or (try-move ctx entity* direction)
        (try-move ctx entity* [xdir 0])
        (try-move ctx entity* [0 ydir]))))

; optional, only assoc'ing movement-vector
(defcomponent :entity/movement data/pos-attr
  (entity/create [[_ tiles-per-second] entity* _ctx]
    (assert (and (:entity/body entity*)
                 (:entity/position entity*)))
    (assert (<= tiles-per-second max-speed)))

  (entity/tick [_ entity* ctx]
    (when-let [direction (:entity/movement-vector entity*)]
      (assert (or (zero? (v/length direction))
                  (v/normalised? direction)))
      (when-not (zero? (v/length direction))
        (when-let [{:keys [entity/id
                           entity/position
                           entity/body]} (if (:solid? (:entity/body entity*))
                                           (update-position-solid     ctx entity* direction)
                                           (update-position-non-solid ctx entity* direction))]
          [[:tx.entity/assoc    id :entity/position position]
           [:tx.entity/assoc-in id [:entity/body :left-bottom] (:left-bottom body)]
           (when (:rotate-in-movement-direction? body)
             [:tx.entity/assoc-in id [:entity/body :rotation-angle] (v/get-angle-from-vector direction)])
           [:tx/position-changed id]])))))
