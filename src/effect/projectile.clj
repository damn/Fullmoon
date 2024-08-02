(ns effect.projectile
  (:require [clojure.string :as str]
            [core.component :refer [defcomponent]]
            [math.vector :as v]
            [data.animation :as animation]
            [api.context :refer [get-sprite spritesheet effect-text path-blocked?]]
            [api.effect :as effect]
            [api.tx :refer [transact!]]))

; -> range needs to be smaller than potential field range
; -> first range check then ray ! otherwise somewhere in contentfield out of sight

(def ^:private size 0.5)
(def ^:private maxrange 10)
(def ^:private speed 10)
(def ^:private maxtime (/ maxrange speed))

(comment
 ; for chance to do hit-effect -> use this code @ hit-effects
 ; (extend hit-effects with chance , not effects themself)
 ; and hit-effects to text ...hmmm
 [utils.random :as random]
 (or (not chance)
     (random/percent-chance chance)))

(def ^:private hit-effect
  [[:effect/damage {:damage/min-max [4 8]}]
   [:effect/stun 0.5]
   ;[:stun {:duration 0.2} {:chance 100}]
   ])

(defn- black-projectile [context]
  (animation/create [(get-sprite context
                                 (spritesheet context "fx/uf_FX.png" 24 24)
                                 [1 12])]
                    :frame-duration 0.5))

(defn- start-point [entity* direction]
  (v/add (:entity/position entity*)
         (v/scale direction
                  (+ (:radius (:entity/body entity*)) size 0.1))))

(defcomponent :effect/projectile {:widget :text-field
                                  :schema [:= true]
                                  :default-value true}
  (effect/text [_ ctx]
    (effect-text ctx hit-effect))

  (effect/valid-params? [_ {:keys [effect/source
                                   effect/target
                                   effect/direction]}]
    (and source direction)) ; faction @ source also ?

  ; TODO valid params direction has to be  non-nil (entities not los player ) ?
  (effect/useful? [_ {:keys [effect/source effect/target] :as ctx}]
    (let [source-p (:entity/position @source)
          target-p (:entity/position @target)]
      (and (not (path-blocked? ctx
                               source-p ; TODO test
                               target-p
                               size))
           ; TODO not taking into account body sizes
           (< (v/distance source-p ; entity/distance function protocol EntityPosition
                          target-p)
              maxrange))))

  (transact! [_ {:keys [effect/source
                        effect/direction] :as ctx}]
    [[:tx/create #:entity {:position (start-point @source direction)
                           :body {:width size
                                  :height size
                                  :solid? false
                                  :rotation-angle (v/get-angle-from-vector direction)}

                           :animation (black-projectile ctx)

                           :flying? true
                           :z-order :z-order/effect

                           :faction (:entity/faction @source)

                           :movement speed
                           :movement-vector direction

                           :delete-after-duration maxtime

                           :plop true
                           :projectile-collision {:hit-effect hit-effect
                                                  :piercing? true}}]]))

; => well defined components
; => what each means, how it interacts, how it depends, what they do
; which I _need_ => entity schema ??? check @ tx/create ? and at debug mode also @ changes ???
