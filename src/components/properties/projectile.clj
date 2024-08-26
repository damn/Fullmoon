(ns components.properties.projectile
  (:require [clojure.string :as str]
            [math.vector :as v]
            [core.component :refer [defcomponent] :as component]
            [core.context :as ctx]
            [core.entity :as entity]))

; TODO speed is 10 tiles/s but I checked moves 8 tiles/sec ... after delta time change ?

; -> range needs to be smaller than potential field range (otherwise hitting someone who can't get back at you)
; -> first range check then ray ! otherwise somewhere in contentfield out of sight
(defcomponent :projectile/max-range {:data :pos-int})
(defcomponent :projectile/speed     {:data :pos-int})
(defcomponent :projectile/effects   {:data [:components-ns :effect]})
(defcomponent :projectile/piercing? {:data :boolean})

(defcomponent :properties/projectile
  (component/create [_ _ctx]
    {:id-namespace "projectiles"
     :schema [[:property/id [:qualified-keyword {:namespace :projectiles}]]
              [:property/image ; TODO what is optional/obligatory??
               :projectile/max-range
               :projectile/speed
               :projectile/effects
               :projectile/piercing?]]
     :edn-file-sort-order 8
     :overview {:title "Projectiles"
                :columns 16
                :image/dimensions [48 48]}
     :->text (fn [ctx {:keys [property/id]}]
               [(str/capitalize (name id))])}))

(defn- projectile-size [projectile-property]
  (first (:world-unit-dimensions (:property/image projectile-property))))

(defcomponent :tx.entity/projectile
  (component/do! [[_ projectile-id {:keys [position direction faction]}]
                ctx]
    (let [{:keys [property/image
                  projectile/max-range
                  projectile/speed
                  projectile/effects
                  projectile/piercing?] :as prop} (ctx/get-property ctx projectile-id)
          size (projectile-size prop)]
      [[:tx/create
        {:position position
         :width size
         :height size
         :z-order :z-order/flying
         :rotation-angle (v/get-angle-from-vector direction)}
        #:entity {:movement {:direction direction
                             :speed speed}
                  :image image
                  :faction faction
                  :delete-after-duration (/ max-range speed)
                  :destroy-audiovisual :audiovisuals/hit-wall
                  :projectile-collision {:hit-effects effects
                                         :piercing? piercing?}}]])))

(defn- start-point [entity* direction size]
  (v/add (:position entity*)
         (v/scale direction
                  (+ (:radius entity*) size 0.1))))

; TODO effect/text ... shouldn't have source/target dmg stuff ....
; as it is just sent .....
; or we adjust the effect when we send it ....
(defcomponent :effect/projectile
  {:data [:qualified-keyword {:namespace :projectiles}]}
  (component/info-text [[_ projectile-id] ctx]
    (ctx/effect-text ctx (:projectile/effects (ctx/get-property ctx projectile-id))))

  ; TODO for npcs need target -- anyway only with direction
  (component/applicable? [_ {:keys [effect/direction]}]
    direction) ; faction @ source also ?

  ; TODO valid params direction has to be  non-nil (entities not los player ) ?
  (component/useful? [[_ projectile-id] {:keys [effect/source effect/target] :as ctx}]
    (let [source-p (:position @source)
          target-p (:position @target)
          prop (ctx/get-property ctx projectile-id)]
      (and (not (ctx/path-blocked? ctx ; TODO test
                                   source-p
                                   target-p
                                   (projectile-size prop)))
           ; TODO not taking into account body sizes
           (< (v/distance source-p ; entity/distance function protocol EntityPosition
                          target-p)
              (:projectile/max-range prop)))))

  (component/do! [[_ projectile-id] {:keys [effect/source effect/direction] :as ctx}]
    [[:tx/sound "sounds/bfxr_waypointunlock.wav"]
     [:tx.entity/projectile
      projectile-id
      {:position (start-point @source
                              direction
                              (projectile-size (ctx/get-property ctx projectile-id)))
       :direction direction
       :faction (:entity/faction @source)}]]))

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
   [:tx.entity/projectile projectile-id ...]
   )
 )
