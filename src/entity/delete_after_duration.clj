(ns entity.delete-after-duration
  (:require [core.component :refer [defcomponent]]
            [api.context :refer [->counter stopped?]]
            [api.entity :as entity]))

(defcomponent :entity/delete-after-duration {}
  (entity/create-component [[_ duration] _components ctx]
    (->counter ctx duration))

  (entity/tick [[_ counter] {:keys [entity/id]} ctx]
    (when (stopped? ctx counter)
      [[:tx/destroy id]])))
