(ns cdq.world.content-grid
  (:require [data.grid2d :as grid2d]
            cdq.api.world.content-grid))

; why needs entity a reference to the cell
; just calculate it easily

(defrecord ContentGrid [grid cell-w cell-h]
  cdq.api.world.content-grid/ContentGrid
  (update-entity! [_ entity]
    (let [{:keys [entity/content-cell entity/position]} @entity
          [x y] position
          new-cell (get grid [(int (/ x cell-w))
                              (int (/ y cell-h))])]
      (when-not (= content-cell new-cell)
        (swap! new-cell update :entities conj entity)
        (swap! entity assoc :entity/content-cell new-cell)
        (when content-cell
          (swap! content-cell update :entities disj entity)))))

  (remove-entity! [_ entity]
    (-> @entity
        :entity/content-cell
        (swap! update :entities disj entity)))

  (active-entities [_ center-entity]
    (->> (let [idx (-> @center-entity
                       :entity/content-cell
                       deref
                       :idx)]
           (cons idx (grid2d/get-8-neighbour-positions idx)))
         (keep grid)
         (mapcat (comp :entities deref)))))

(defn ->content-grid [w h cell-w cell-h]
  (->ContentGrid (grid2d/create-grid (inc (int (/ w cell-w))) ; inc because corners
                                     (inc (int (/ h cell-h)))
                                     (fn [idx]
                                       (atom {:idx idx,
                                              :entities #{}})))
                 cell-w
                 cell-h))

(comment

 (defn get-all-entities-of-current-map [context]
   (mapcat (comp :entities deref)
           (grid2d/cells (cdq.api.context/content-grid context))))

 (count
  (get-all-entities-of-current-map @gdl.app/current-context))

 )
