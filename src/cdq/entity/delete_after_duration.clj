(ns cdq.entity.delete-after-duration
  (:require [core.component :as component]
            [cdq.api.context :refer [->counter stopped?]]
            [cdq.api.entity :as entity]))

(component/def :entity/delete-after-duration {}
  counter
  (entity/create-component [[_ duration] _components ctx]
    (->counter ctx duration))

  (entity/tick [_ {:keys [entity/id]} ctx]
    (when (stopped? ctx counter)
      [[:tx/destroy id]])))
