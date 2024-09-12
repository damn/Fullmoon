(ns components.entity.shout
  (:require [core.component :as component :refer [defcomponent]]
            [core.context :as ctx]
            [core.entity :as entity]
            [core.world.grid :as grid]))

(def ^:private shout-radius 4)

(defn- friendlies-in-radius [ctx position faction]
  (->> {:position position
        :radius shout-radius}
       (grid/circle->entities (ctx/world-grid ctx))
       (map deref)
       (filter #(= (:entity/faction %) faction))
       (map :entity/id)))

(defcomponent ::alert-friendlies-after-duration
  {:let {:keys [counter faction]}}
  (component/tick [_ eid ctx]
    (when (ctx/stopped? ctx counter)
      (cons [:tx/destroy eid]
            (for [friendly-eid (friendlies-in-radius ctx (:position @eid) faction)]
              [:tx/event friendly-eid :alert])))))

(defcomponent :tx/shout
  (component/do! [[_ position faction delay-seconds] ctx]
    [[:tx/create
      position
      entity/effect-body-props
      {::alert-friendlies-after-duration
       {:counter (ctx/->counter ctx delay-seconds)
        :faction faction}}]]))
