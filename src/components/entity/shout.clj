(ns components.entity.shout
  (:require [core.component :refer [defcomponent]]
            [core.context :as ctx :refer [world-grid line-of-sight? stopped?]]
            [core.entity :as entity]
            [core.effect :as effect]
            [core.world.grid :refer [circle->entities]]))

(def ^:private shout-range 3)

; TODO gets itself also
  ; == faction/friendly? e1 e2 ( entity*/friendly? e*1 e*2) ?
(defn- get-friendly-entities-in-line-of-sight [context entity* radius]
  (->> {:position (:position entity*)
        :radius radius}
       (circle->entities (world-grid context))
       (map deref)
       (filter #(and (= (:entity/faction %) (:entity/faction entity*))
                     (line-of-sight? context entity* %)))))

(defcomponent :entity/shout {}
  (entity/tick [[_ counter] entity context]
    (when (stopped? context counter)
      (cons [:tx/destroy entity]
            (for [{:keys [entity/id]} (get-friendly-entities-in-line-of-sight context @entity shout-range)]
              [:tx/event entity :alert])))))

(defcomponent :tx.entity/shout {}
  (effect/do! [[_ position faction delay-seconds] ctx]
    [[:tx/create
      {:position position
       :width 0.5
       :height 0.5
       :z-order :z-order/effect}
      #:entity {:faction faction
                :shout (ctx/->counter ctx delay-seconds)}]]))
