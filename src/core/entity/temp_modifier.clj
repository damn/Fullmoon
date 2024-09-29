(in-ns 'core.entity)

(defcomponent :entity/temp-modifier
  {:let {:keys [counter modifiers]}}
  (info-text [_ ctx]
    (str "[LIGHT_GRAY]Spiderweb - remaining: " (readable-number (finished-ratio ctx counter)) "/1[]"))

  (tick [[k _] eid ctx]
    (when (stopped? ctx counter)
      [[:e/dissoc eid k]
       [:tx/reverse-modifiers eid modifiers]]))

  (render-above [_ entity* g _ctx]
    (draw-filled-circle g (:position entity*) 0.5 [0.5 0.5 0.5 0.4])))
