(ns core.entity.delete-after-duration
  (:require [utils.core :refer [readable-number]]
            [core.component :as component :refer [defcomponent]]
            [core.entity :as entity]
            [core.ctx.time :as time]))

(defcomponent :entity/delete-after-duration
  {:let counter}
  (component/create [[_ duration] ctx]
    (time/->counter ctx duration))

  (component/info-text [_ ctx]
    (str "[LIGHT_GRAY]Remaining: " (readable-number (time/finished-ratio ctx counter)) "/1[]"))

  (entity/tick [_ eid ctx]
    (when (time/stopped? ctx counter)
      [[:e/destroy eid]])))
