(ns properties.projectile
  (:require [clojure.string :as str]
            [math.vector :as v]
            [core.component :refer [defcomponent]]
            [core.data :as data]
            [api.context :refer [get-sprite spritesheet path-blocked?]]
            [api.effect :as effect]
            [api.entity :as entity]
            [api.properties :as properties]
            [api.tx :refer [transact!]]
            [effect-ctx.core :as effect-ctx]))

(defcomponent :properties/projectile {}
  (properties/create [_]
    {:id-namespace "projectiles"
     :schema (data/map-attribute-schema
              [:property/id [:qualified-keyword {:namespace :skills}]]
              [:property/image])
     :edn-file-sort-order 0
     :overview {:title "Projectile"
                :columns 16
                :image/dimensions [48 48]}
     :->text (fn [ctx {:keys [property/id]}]
               [(str/capitalize (name id))])}))

; -> range needs to be smaller than potential field range (otherwise hitting someone who can't get back at you)
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

(def ^:private hit-effects
  [[:effect/damage {:damage/min-max [4 8]}]
   [:effect/stun 0.5]
   ;[:stun {:duration 0.2} {:chance 100}]
   ])

(defn- black-projectile [context]
  (get-sprite context
              (spritesheet context "fx/uf_FX.png" 24 24)
              [1 12]))

(defmethod transact! :tx.entity/projectile [[_ {:keys [position direction faction]}] ctx]
  [[:tx/create #:entity {:body {:position position
                                :width size
                                :height size
                                :solid? false
                                :z-order :z-order/flying
                                :rotation-angle (v/get-angle-from-vector direction)
                                :movement {:direction direction :speed speed}}
                         :image (black-projectile ctx)
                         :faction faction
                         :delete-after-duration maxtime
                         :plop true
                         :projectile-collision {:hit-effects hit-effects
                                                :piercing? true}}]])


(defn- start-point [entity* direction]
  (v/add (entity/position entity*)
         (v/scale direction
                  (+ (:radius (:entity/body entity*)) size 0.1))))

(defcomponent :effect/projectile {:widget :text-field
                                  :schema [:= true]
                                  :default-value true}
  (effect/text [_ effect-ctx]
    (effect-ctx/text effect-ctx hit-effects))

  ; TODO for npcs need target -- anyway only with direction
  (effect/usable? [_ {:keys [effect/direction]}]
    direction) ; faction @ source also ?

  ; TODO valid params direction has to be  non-nil (entities not los player ) ?
  (effect/useful? [_ {:keys [effect/source effect/target]} ctx]
    (let [source-p (entity/position @source)
          target-p (entity/position @target)]
      (and (not (path-blocked? ctx
                               source-p ; TODO test
                               target-p
                               size))
           ; TODO not taking into account body sizes
           (< (v/distance source-p ; entity/distance function protocol EntityPosition
                          target-p)
              maxrange))))

  (transact! [_ {:keys [effect/source effect/direction]}]
    [[:tx/sound "sounds/bfxr_waypointunlock.wav"]
     [:tx.entity/projectile {:position (start-point @source direction)
                             :direction direction
                             :faction (:entity/faction @source)}]]))

; => well defined components
; => what each means, how it interacts, how it depends, what they do
; which I _need_ => entity schema ??? check @ tx/create ? and at debug mode also @ changes ???
