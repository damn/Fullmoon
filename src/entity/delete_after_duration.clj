(ns entity.delete-after-duration
  (:require [core.component :refer [defcomponent]]
            [api.context :as ctx :refer [->counter stopped?]]
            [api.entity :as entity]))

(defcomponent :entity/delete-after-duration {}
  (entity/create-component [[_ duration] _components ctx]
    (->counter ctx duration))

  (entity/tick [[_ counter] {:keys [entity/id]} ctx]
    (when (stopped? ctx counter)
      [[:tx/destroy id]]))

  ; TODO bar like in wc3 blue (water-elemental)
  (entity/info-text [[_ counter] ctx]
    (str "[LIGHT_GRAY]Remaining: "
         (utils.core/readable-number (ctx/finished-ratio ctx counter))
         "/1[]")))
