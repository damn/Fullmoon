(ns core.effect.target
  (:require [core.math.vector :as v]
            [core.ctx :refer :all]
            [core.effect :as effect]
            [core.entity :as entity]
            [core.world :refer [active-entities]]))

(defcomponent :entity-effects {:data [:components-ns :effect.entity]})

; TODO applicable targets? e.g. projectiles/effect s/???item entiteis ??? check
; same code as in render entities on world view screens/world
(defn- creatures-in-los-of-player [ctx]
  (->> (active-entities ctx)
       (filter #(:creature/species @%))
       (filter #(entity/line-of-sight? ctx (player-entity* ctx) @%))
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

  (effect/render [_ g {:keys [effect/source] :as ctx}]
    (let [source* @source]
      (doseq [target* (map deref (creatures-in-los-of-player ctx))]
        (draw-line g
                   (:position source*) #_(start-point source* target*)
                   (:position target*)
                   [1 0 0 0.5])))))

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

  (effect/render [_ g {:keys [effect/source effect/target]}]
    (let [source* @source
          target* @target]
      (draw-line g
                 (start-point source* target*)
                 (end-point   source* target* maxrange)
                 (if (in-range? source* target* maxrange)
                   [1 0 0 0.5]
                   [1 1 0 0.5])))))
