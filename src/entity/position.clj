(ns entity.position
  (:require [core.component :refer [defcomponent]]
            [math.vector :as v]
            [utils.core :refer [->tile]]
            [api.entity :as entity]))

(extend-type api.entity.Entity
  entity/Position
  (tile [{:keys [entity/position]}]
    (->tile position))

  (direction [{:keys [entity/position]} other-entity*]
    (v/direction position (:entity/position other-entity*))))

(defcomponent :entity/position {}
  (entity/create [_ {:keys [entity/id]} ctx]
    [[:tx/add-to-world id]])

  (entity/destroy [_ {:keys [entity/id]} ctx]
    [[:tx/remove-from-world id]]))
