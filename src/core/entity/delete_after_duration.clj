(ns ^:no-doc core.entity.delete-after-duration
  (:require [core.utils.core :refer [readable-number]]
            [core.ctx :refer :all]
            [core.entity :as entity]
            [core.world.time :as time]))

(defcomponent :entity/delete-after-duration
  {:let counter}
  (->mk [[_ duration] ctx]
    (time/->counter ctx duration))

  (info-text [_ ctx]
    (str "[LIGHT_GRAY]Remaining: " (readable-number (time/finished-ratio ctx counter)) "/1[]"))

  (entity/tick [_ eid ctx]
    (when (time/stopped? ctx counter)
      [[:e/destroy eid]])))
