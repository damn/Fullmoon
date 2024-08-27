(ns components.effect.target-all
  (:require [math.vector :as v]
            [core.component :as component :refer [defcomponent]]
            [core.components :as components]
            [core.graphics :as g]
            [core.context :as ctx]
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

(defcomponent :effect/target-all
  {:data [:map :entity-effects]
   :let {:keys [entity-effects]}}
  (component/info-text [_ ctx]
    (str "All visible targets\n" (components/info-text entity-effects ctx)))

  (component/applicable? [_ _ctx]
    true)

  (component/useful? [_ _ctx]
    ; TODO
    false
    )

  (component/do! [_ {:keys [effect/source effect/target] :as ctx}]
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
                [:tx/effect {:effect/source source :effect/target target} entity-effects]]))))

  (component/render [_ g {:keys [effect/source effect/target] :as ctx}]
    (let [source* @source]
      (doseq [target* (map deref (all-targets ctx))]
        (g/draw-line g
                     (:position source*) #_(start-point source* target*)
                     (:position target*)
                     [1 0 0 0.5])))))
