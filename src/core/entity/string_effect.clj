(in-ns 'core.entity)

(defcomponent :entity/string-effect
  (tick [[k {:keys [counter]}] eid ctx]
    (when (stopped? ctx counter)
      [[:e/dissoc eid k]]))

  (render-above [[_ {:keys [text]}] entity* g _ctx]
    (let [[x y] (:position entity*)]
      (draw-text g
                 {:text text
                  :x x
                  :y (+ y (:half-height entity*) (pixels->world-units g hpbar-height-px))
                  :scale 2
                  :up? true}))))

(defcomponent :tx/add-text-effect
  (do! [[_ entity text] ctx]
    [[:e/assoc
      entity
      :entity/string-effect
      (if-let [string-effect (:entity/string-effect @entity)]
        (-> string-effect
            (update :text str "\n" text)
            (update :counter #(reset ctx %)))
        {:text text
         :counter (->counter ctx 0.4)})]]))

