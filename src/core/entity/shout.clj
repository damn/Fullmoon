(ns core.entity.shout
  (:require [core.component :refer [defcomponent]]
            [world.context :refer [world-grid]]
            [core.entity :as entity]
            [core.time :as time]
            [core.tx :as tx]
            [core.world.grid :as grid]))

(def ^:private shout-radius 4)

(defn- friendlies-in-radius [ctx position faction]
  (->> {:position position
        :radius shout-radius}
       (grid/circle->entities (world-grid ctx))
       (map deref)
       (filter #(= (:entity/faction %) faction))
       (map :entity/id)))

(defcomponent ::alert-friendlies-after-duration
  {:let {:keys [counter faction]}}
  (entity/tick [_ eid ctx]
    (when (time/stopped? ctx counter)
      (cons [:tx/destroy eid]
            (for [friendly-eid (friendlies-in-radius ctx (:position @eid) faction)]
              [:tx/event friendly-eid :alert])))))

(defcomponent :tx/shout
  (tx/do! [[_ position faction delay-seconds] ctx]
    [[:tx/create
      position
      entity/effect-body-props
      {::alert-friendlies-after-duration
       {:counter (time/->counter ctx delay-seconds)
        :faction faction}}]]))
