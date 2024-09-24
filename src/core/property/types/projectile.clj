(ns ^:no-doc core.property.types.projectile
  (:require [core.math.vector :as v]
            [core.ctx :refer :all]
            [core.entity :as entity]
            [core.world.raycaster :refer [path-blocked?]]))

; TODO speed is 10 tiles/s but I checked moves 8 tiles/sec ... after delta time change ?

; -> range needs to be smaller than potential field range (otherwise hitting someone who can't get back at you)
; -> first range check then ray ! otherwise somewhere in contentfield out of sight
(defcomponent :projectile/max-range {:data :pos-int})
(defcomponent :projectile/speed     {:data :pos-int})

(defcomponent :projectile/piercing? {:data :boolean}
  (info-text [_ ctx] "[LIME]Piercing[]"))

(def-type :properties/projectiles
  {:schema [:entity/image
            :projectile/max-range
            :projectile/speed
            :projectile/piercing?
            :entity-effects]
   :overview {:title "Projectiles"
              :columns 16
              :image/scale 2}})

(defn- projectile-size [projectile]
  {:pre [(:entity/image projectile)]}
  (first (:world-unit-dimensions (:entity/image projectile))))

(defcomponent :tx/projectile
  (do! [[_
            {:keys [position direction faction]}
            {:keys [entity/image
                    projectile/max-range
                    projectile/speed
                    entity-effects
                    projectile/piercing?] :as projectile}]
           ctx]
    (let [size (projectile-size projectile)]
      [[:e/create
        position
        {:width size
         :height size
         :z-order :z-order/flying
         :rotation-angle (v/get-angle-from-vector direction)}
        {:entity/movement {:direction direction
                           :speed speed}
         :entity/image image
         :entity/faction faction
         :entity/delete-after-duration (/ max-range speed)
         :entity/destroy-audiovisual :audiovisuals/hit-wall
         :entity/projectile-collision {:entity-effects entity-effects
                                       :piercing? piercing?}}]])))

(defn- start-point [entity* direction size]
  (v/add (:position entity*)
         (v/scale direction
                  (+ (:radius entity*) size 0.1))))

; TODO effect/text ... shouldn't have source/target dmg stuff ....
; as it is just sent .....
; or we adjust the effect when we send it ....

(defcomponent :effect/projectile
  {:data [:one-to-one :properties/projectiles]
   :let {:keys [entity-effects projectile/max-range] :as projectile}}
  ; TODO for npcs need target -- anyway only with direction
  (applicable? [_ {:keys [effect/direction]}]
    direction) ; faction @ source also ?

  ; TODO valid params direction has to be  non-nil (entities not los player ) ?
  (useful? [_ {:keys [effect/source effect/target] :as ctx}]
    (let [source-p (:position @source)
          target-p (:position @target)]
      (and (not (path-blocked? ctx ; TODO test
                               source-p
                               target-p
                               (projectile-size projectile)))
           ; TODO not taking into account body sizes
           (< (v/distance source-p ; entity/distance function protocol EntityPosition
                          target-p)
              max-range))))

  (do! [_ {:keys [effect/source effect/direction] :as ctx}]
    [[:tx/sound "sounds/bfxr_waypointunlock.wav"]
     [:tx/projectile
      {:position (start-point @source direction (projectile-size projectile))
       :direction direction
       :faction (:entity/faction @source)}
      projectile]]))

(comment
 ; mass shooting
 (for [direction (map math.vector/normalise
                      [[1 0]
                       [1 1]
                       [1 -1]
                       [0 1]
                       [0 -1]
                       [-1 -1]
                       [-1 1]
                       [-1 0]])]
   [:tx/projectile projectile-id ...]
   )
 )
