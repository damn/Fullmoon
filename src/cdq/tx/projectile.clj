(ns cdq.tx.projectile
  (:require [clojure.string :as str]
            [core.component :as component]
            [gdl.math.vector :as v]
            [gdl.graphics.animation :as animation]
            [gdl.context :refer [get-sprite spritesheet]]
            [cdq.api.context :refer [transact! effect-text path-blocked?]]
            [cdq.api.effect :as effect]))

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
  [[:tx/damage {:damage/min-max [4 8]}]
   [:tx/stun 0.5]
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

(component/def :tx/projectile {:widget :text-field
                               :schema [:= true]
                               :default-value true}
  _
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
                                    :faction (:entity/faction @source)
                                    :body {:width size
                                           :height size
                                           :solid? false
                                           :rotation-angle (v/get-angle-from-vector direction)}
                                    :flying? true
                                    :z-order :z-order/effect
                                    :movement speed
                                    :movement-vector direction
                                    :animation (black-projectile ctx)
                                    :delete-after-duration maxtime
                                    :plop true
                                    :projectile-collision {:hit-effect hit-effect
                                                           :piercing? true}}]]))
