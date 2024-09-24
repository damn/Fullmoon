(ns ^:no-doc core.ctx.content-grid
  (:require [data.grid2d :as grid2d]
            [core.ctx :refer :all]))

(def ^:private this :context/content-grid)

(defn update-entity! [ctx entity]
  (let [{:keys [grid cell-w cell-h]} (this ctx)
        {::keys [content-cell] :as entity*} @entity
        [x y] (:position entity*)
        new-cell (get grid [(int (/ x cell-w))
                            (int (/ y cell-h))])]
    (when-not (= content-cell new-cell)
      (swap! new-cell update :entities conj entity)
      (swap! entity assoc ::content-cell new-cell)
      (when content-cell
        (swap! content-cell update :entities disj entity)))))

(defn remove-entity! [_ entity]
  (-> @entity
      ::content-cell
      (swap! update :entities disj entity)))

(defn active-entities [ctx center-entity*]
  (let [{:keys [grid]} (this ctx)]
    (->> (let [idx (-> center-entity*
                       ::content-cell
                       deref
                       :idx)]
           (cons idx (grid2d/get-8-neighbour-positions idx)))
         (keep grid)
         (mapcat (comp :entities deref)))))

(defcomponent this
  {:let [cell-w cell-h]}
  (->mk [_ {:keys [context/grid]}]
    {:grid (grid2d/create-grid (inc (int (/ (grid2d/width grid) cell-w))) ; inc because corners
                               (inc (int (/ (grid2d/height grid) cell-h)))
                               (fn [idx]
                                 (atom {:idx idx,
                                        :entities #{}})))
     :cell-w cell-w
     :cell-h cell-h}))

(comment

 (defn get-all-entities-of-current-map [context]
   (mapcat (comp :entities deref)
           (grid2d/cells (core.context/content-grid context))))

 (count
  (get-all-entities-of-current-map @app/state))

 )
