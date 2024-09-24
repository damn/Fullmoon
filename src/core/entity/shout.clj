(ns ^:no-doc core.entity.shout
  (:require [core.ctx :refer :all]
            [core.entity :as entity]
            [core.world.time :as time]
            [core.world.grid :as grid]))

(def ^:private shout-radius 4)

(defn- friendlies-in-radius [ctx position faction]
  (->> {:position position
        :radius shout-radius}
       (grid/circle->entities (:context/grid ctx))
       (map deref)
       (filter #(= (:entity/faction %) faction))
       (map :entity/id)))

(defcomponent ::alert-friendlies-after-duration
  {:let {:keys [counter faction]}}
  (entity/tick [_ eid ctx]
    (when (time/stopped? ctx counter)
      (cons [:e/destroy eid]
            (for [friendly-eid (friendlies-in-radius ctx (:position @eid) faction)]
              [:tx/event friendly-eid :alert])))))

(defcomponent :tx/shout
  (do! [[_ position faction delay-seconds] ctx]
    [[:e/create
      position
      entity/effect-body-props
      {::alert-friendlies-after-duration
       {:counter (time/->counter ctx delay-seconds)
        :faction faction}}]]))
