(ns world.effect.target
  (:require [component.core :refer [defc]]
            [component.info :as info]
            [component.tx :as tx]
            [gdx.graphics :as g]
            [gdx.math.vector :as v]
            [world.core :as world]
            [world.entity :as entity]
            [world.effect :as effect :refer [source target]]))

(defc :entity-effects {:schema [:s/components-ns :effect.entity]})

; TODO applicable targets? e.g. projectiles/effect s/???item entiteis ??? check
; same code as in render entities on world view screens/world
(defn- creatures-in-los-of-player []
  (->> (world/active-entities)
       (filter #(:creature/species @%))
       (filter #(world/line-of-sight? @world/player @%))
       (remove #(:entity/player? @%))))

; TODO targets projectiles with -50% hp !!

(comment
 ; TODO showing one a bit further up
 ; maybe world view port is cut
 ; not quite showing correctly.
 (let [targets (creatures-in-los-of-player)]
   (count targets)
   #_(sort-by #(% 1) (map #(vector (:entity.creature/name @%)
                                   (:position @%)) targets)))

 )

(defc :effect/target-all
  {:schema [:s/map [:entity-effects]]
   :let {:keys [entity-effects]}}
  (info/text [_]
    "[LIGHT_GRAY]All visible targets[]")

  (effect/applicable? [_]
    true)

  (effect/useful? [_]
    ; TODO
    false
    )

  (tx/do! [_]
    (let [source* @source]
      (apply concat
             (for [target (creatures-in-los-of-player)]
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

  (effect/render! [_]
    (let [source* @source]
      (doseq [target* (map deref (creatures-in-los-of-player))]
        (g/draw-line (:position source*) #_(start-point source* target*)
                     (:position target*)
                     [1 0 0 0.5])))))

(defn- in-range? [entity target* maxrange] ; == circle-collides?
  (< (- (float (v/distance (:position entity)
                           (:position target*)))
        (float (:radius entity))
        (float (:radius target*)))
     (float maxrange)))

; TODO use at projectile & also adjust rotation
(defn- start-point [entity target*]
  (v/add (:position entity)
         (v/scale (entity/direction entity target*)
                  (:radius entity))))

(defn- end-point [entity target* maxrange]
  (v/add (start-point entity target*)
         (v/scale (entity/direction entity target*)
                  maxrange)))

(defc :maxrange {:schema pos?}
  (info/text [[_ maxrange]]
    (str "[LIGHT_GRAY]Range " maxrange " meters[]")))

(defc :effect/target-entity
  {:let {:keys [maxrange entity-effects]}
   :schema [:s/map [:entity-effects :maxrange]]
   :editor/doc "Applies entity-effects to a target if they are inside max-range & in line of sight.
               Cancels if line of sight is lost. Draws a red/yellow line wheter the target is inside the max range. If the effect is to be done and target out of range -> draws a hit-ground-effect on the max location."}

  (effect/applicable? [_]
    (and target
         (effect/effect-applicable? entity-effects)))

  (effect/useful? [_]
    (assert source)
    (assert target)
    (in-range? @source @target maxrange))

  (tx/do! [_]
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

         ; TODO FIXME
         ; have to use tx/effect now ?!
         ; still same context ...
         ; filter applicable ?! - omg
         entity-effects

         )
        [; TODO
         ; * clicking on far away monster
         ; * hitting ground in front of you ( there is another monster )
         ; * -> it doesn't get hit ! hmmm
         ; * either use 'MISS' or get enemy entities at end-point
         [:tx/audiovisual (end-point source* target* maxrange) :audiovisuals/hit-ground]])))

  (effect/render! [_]
    (when target
      (let [source* @source
            target* @target]
        (g/draw-line (start-point source* target*)
                     (end-point source* target* maxrange)
                     (if (in-range? source* target* maxrange)
                       [1 0 0 0.5]
                       [1 1 0 0.5]))))))
