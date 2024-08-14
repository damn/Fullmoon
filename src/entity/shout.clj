(ns entity.shout
  (:require [core.component :refer [defcomponent]]
            [api.context :as ctx :refer [world-grid line-of-sight? stopped?]]
            [api.entity :as entity]
            [api.tx :refer [transact!]]
            [api.world.grid :refer [circle->entities]]))

(def ^:private shout-range 3)

; TODO gets itself also
  ; == faction/friendly? e1 e2 ( entity*/friendly? e*1 e*2) ?
(defn- get-friendly-entities-in-line-of-sight [context entity* radius]
  (->> {:position (entity/position entity*)
        :radius radius}
       (circle->entities (world-grid context))
       (map deref)
       (filter #(and (= (:entity/faction %) (:entity/faction entity*))
                     (line-of-sight? context entity* %)))))

(defcomponent :entity/shout {}
  (entity/tick [[_ counter] {:keys [entity/id] :as entity*} context]
    (when (stopped? context counter)
      (cons [:tx/destroy id]
            (for [{:keys [entity/id]} (get-friendly-entities-in-line-of-sight context entity* shout-range)]
              [:tx/event id :alert])))))

(defmethod transact! :tx.entity/shout [[_ position faction delay-seconds] ctx]
  [[:tx/create #:entity {:body {:position position
                                :width 0.5
                                :height 0.5
                                :z-order :z-order/effect ; ?
                                }
                         :faction faction
                         :shout (ctx/->counter ctx delay-seconds)}]])
