(ns core.effect
  (:require [clojure.gdx.graphics :as g]
            [clojure.gdx.math.vector :as v]
            [core.component :refer [defsystem defc]]
            [world.entity :as entity]
            [world.entity.faction :as faction]
            [world.mouseover-entity :refer [mouseover-eid]]
            [core.tx :as tx]
            [world.grid :as grid :refer [world-grid]]))

(defsystem applicable?
  "An effect will only be done (with tx/do!) if this function returns truthy.
Required system for every effect, no default.")

(defsystem useful?
  "Used for NPC AI.
Called only if applicable? is truthy.
For example use for healing effect is only useful if hitpoints is < max.
Default method returns true.")
(defmethod useful? :default [_] true)

(defsystem render!  "Renders effect during active-skill state while active till done?. Default do nothing.")
(defmethod render! :default [_])

;;
;; Aggregate functions
;;

(defn- filter-applicable? [effect]
  (filter applicable? effect))

(defn effect-applicable? [effect]
  (seq (filter-applicable? effect)))

(defn effect-useful? [effect]
  (->> effect
       filter-applicable?
       (some useful?)))

;;

(defn- nearest-enemy [entity]
  (grid/nearest-entity @(world-grid (entity/tile entity))
                       (faction/enemy entity)))

;;

; SCHEMA effect-ctx
; * source = always available
; # npc:
;   * target = maybe
;   * direction = maybe
; # player
;  * target = maybe
;  * target-position  = always available (mouse world position)
;  * direction  = always available (from mouse world position)

(declare ^:dynamic source
         ^:dynamic target
         ^:dynamic target-direction
         ^:dynamic target-position)

(defn npc-ctx [eid]
  (let [entity @eid
        target (nearest-enemy entity)
        target (when (and target (entity/line-of-sight? entity @target))
                 target)]
    {:effect/source eid
     :effect/target target
     :effect/target-direction (when target (entity/direction entity @target))}))

(defn player-ctx [eid]
  (let [target-position (or (and mouseover-eid (:position @mouseover-eid))
                            (g/world-mouse-position))]
    {:effect/source eid
     :effect/target mouseover-eid
     :effect/target-position target-position
     :effect/target-direction (v/direction (:position @eid) target-position)}))

; this is not necessary if effect does not need target, but so far not other solution came up.
(defn check-update-ctx
  "Call this on effect-context if the time of using the context is not the time when context was built."
  [{:keys [effect/source effect/target] :as ctx}]
  (if (and target
           (not (:entity/destroyed? @target))
           (entity/line-of-sight? @source @target))
    ctx
    (dissoc ctx :effect/target)))

(defmacro with-ctx [ctx & body]
  `(binding [source           (:effect/source           ~ctx)
             target           (:effect/target           ~ctx)
             target-direction (:effect/target-direction ~ctx)
             target-position  (:effect/target-position  ~ctx)]
     ~@body))

(defc :tx/effect
  (tx/do! [[_ effect-ctx effect]]
    (with-ctx effect-ctx
      (tx/do-all (filter-applicable? effect)))))
