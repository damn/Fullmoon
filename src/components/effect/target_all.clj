(ns components.effect.target-all
  (:require [math.vector :as v]
            [core.component :refer [defcomponent]]
            [core.data :as data]
            [core.graphics :as g]
            [core.context :as ctx]
            [core.effect :as effect]
            [core.entity :as entity]
            components.context.world))

; TODO applicable targets? e.g. projectiles/effect s/???item entiteis ??? check
; same code as in render entities on world view screens/world
(defn- all-targets [ctx]
  (->> (components.context.world/active-entities ctx)
       (filter #(:z-order @%)) ; all have anyway ? all have body now ?
       (filter #(ctx/line-of-sight? ctx (ctx/player-entity* ctx) @%))
       (remove #(:entity/player? @%))))

(comment
 ; TODO showing one a bit further up
 ; maybe world view port is cut
 ; not quite showing correctly.
 (let [ctx @app/state
       targets (all-targets ctx)]
   (count targets)
   #_(sort-by #(% 1) (map #(vector (:entity.creature/name @%)
                                   (:position @%)) targets)))

 )

(defcomponent :hit-effects (data/components-attribute :effect))

(defcomponent :effect/target-all {:widget :nested-map
                                  :schema [:map {:closed true}
                                           [:hit-effects [:map]] ]
                                  :default-value {:hit-effects {}}}
  {:keys [hit-effects]}
  (effect/text [_ ctx]
    (str "All visible targets:" (ctx/effect-text ctx hit-effects)))

  (effect/applicable? [_ _ctx] true)

  (effect/useful? [_ _ctx]
    ; TODO
    false
    )

  (effect/do! [_ {:keys [effect/source effect/target] :as ctx}]
    (let [source* @source]
      (apply concat
             (for [target (all-targets ctx)]
               [[:tx.entity/line-render {:start (:position source*) #_(start-point source* target*)
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
                [:tx/effect {:effect/source source :effect/target target} hit-effects]]))))

  (effect/render-info [_ g {:keys [effect/source effect/target] :as ctx}]
    (let [source* @source]
      (doseq [target* (map deref (all-targets ctx))]
        (g/draw-line g
                     (:position source*) #_(start-point source* target*)
                     (:position target*)
                     [1 0 0 0.5])))))
