(ns components.effect.target-entity
  (:require [math.vector :as v]
            [core.component :as component :refer [defcomponent]]
            [core.context :as ctx]
            [core.effect :as effect]
            [core.entity :as entity]
            [core.graphics :as g]
            [core.tx :as tx]))

(defn- in-range? [entity* target* maxrange] ; == circle-collides?
  (< (- (float (v/distance (:position entity*)
                           (:position target*)))
        (float (:radius entity*))
        (float (:radius target*)))
     (float maxrange)))

; TODO use at projectile & also adjust rotation
(defn- start-point [entity* target*]
  (v/add (:position entity*)
         (v/scale (entity/direction entity* target*)
                  (:radius entity*))))

(defn- end-point [entity* target* maxrange]
  (v/add (start-point entity* target*)
         (v/scale (entity/direction entity* target*)
                  maxrange)))

(defcomponent :maxrange {:data :pos}
  (component/info-text [[_ maxrange] _ctx]
    (str "[LIGHT_GRAY]Range " maxrange " meters[]")))

(defcomponent :effect/target-entity
  {:let {:keys [maxrange entity-effects]}
   :data [:map [:entity-effects :maxrange]]
   :editor/doc "Applies entity-effects to a target if they are inside max-range & in line of sight.
        Cancels if line of sight is lost. Draws a red/yellow line wheter the target is inside the max range. If the effect is to be done and target out of range -> draws a hit-ground-effect on the max location."}

  (effect/applicable? [_ {:keys [effect/target] :as ctx}]
    (and target
         (some #(effect/applicable? % ctx) entity-effects)))

  (effect/useful? [_ {:keys [effect/source effect/target]}]
    (assert source)
    (assert target)
    (in-range? @source @target maxrange))

  (tx/do! [_ {:keys [effect/source effect/target]}]
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

  (effect/render [_ g {:keys [effect/source effect/target]}]
    (let [source* @source
          target* @target]
      (g/draw-line g
                   (start-point source* target*)
                   (end-point   source* target* maxrange)
                   (if (in-range? source* target* maxrange)
                     [1 0 0 0.5]
                     [1 1 0 0.5])))))
