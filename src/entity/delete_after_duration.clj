(ns entity.delete-after-duration
  (:require [utils.core :refer [readable-number]]
            [core.component :as component :refer [defcomponent]]
            [api.context :as ctx]
            [api.entity :as entity]))

(defcomponent :entity/delete-after-duration {}
  (component/create [[_ duration] ctx]
    (ctx/->counter ctx duration))

  (component/info-text [[_ counter] ctx]
    (str "[LIGHT_GRAY]Remaining: " (readable-number (ctx/finished-ratio ctx counter)) "/1[]"))

  (entity/tick [[_ counter] eid ctx]
    (when (ctx/stopped? ctx counter)
      [[:tx/destroy eid]])))
