(ns world.content-grid
  (:require [data.grid2d :as g2d]
            [world.core :as world]))

(declare ^:private content-grid)

(defn init! [& {:keys [cell-size width height]}]
  (.bindRoot
   #'content-grid
   {:grid (g2d/create-grid (inc (int (/ width  cell-size))) ; inc because corners
                           (inc (int (/ height cell-size)))
                           (fn [idx]
                             (atom {:idx idx,
                                    :entities #{}})))
    :cell-w cell-size
    :cell-h cell-size}))

(defn update-entity! [eid]
  (let [{:keys [grid cell-w cell-h]} content-grid
        {::keys [content-cell] :as entity} @eid
        [x y] (:position entity)
        new-cell (get grid [(int (/ x cell-w))
                            (int (/ y cell-h))])]
    (when-not (= content-cell new-cell)
      (swap! new-cell update :entities conj eid)
      (swap! eid assoc ::content-cell new-cell)
      (when content-cell
        (swap! content-cell update :entities disj eid)))))

(defn remove-entity! [eid]
  (-> @eid
      ::content-cell
      (swap! update :entities disj eid)))

(defn- active-entities* [center-entity]
  (let [{:keys [grid]} content-grid]
    (->> (let [idx (-> center-entity
                       ::content-cell
                       deref
                       :idx)]
           (cons idx (g2d/get-8-neighbour-positions idx)))
         (keep grid)
         (mapcat (comp :entities deref)))))

(defn active-entities []
  (active-entities* @world/player))
