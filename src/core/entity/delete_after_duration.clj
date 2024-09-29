(in-ns 'core.entity)

(defcomponent :entity/delete-after-duration
  {:let counter}
  (->mk [[_ duration] ctx]
    (->counter ctx duration))

  (info-text [_ ctx]
    (str "[LIGHT_GRAY]Remaining: " (readable-number (finished-ratio ctx counter)) "/1[]"))

  (tick [_ eid ctx]
    (when (stopped? ctx counter)
      [[:e/destroy eid]])))
