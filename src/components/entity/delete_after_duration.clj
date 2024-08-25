(ns components.entity.delete-after-duration
  (:require [utils.core :refer [readable-number]]
            [core.component :as component :refer [defcomponent]]
            [core.context :as ctx]))

(defcomponent :entity/delete-after-duration
  {:let counter}
  (component/create [[_ duration] ctx]
    (ctx/->counter ctx duration))

  (component/info-text [_ ctx]
    (str "[LIGHT_GRAY]Remaining: " (readable-number (ctx/finished-ratio ctx counter)) "/1[]"))

  (component/tick [_ eid ctx]
    (when (ctx/stopped? ctx counter)
      [[:tx/destroy eid]])))
