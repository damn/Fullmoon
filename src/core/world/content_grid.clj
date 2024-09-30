(in-ns 'core.world)

(def ^:private content-grid :context/content-grid)

(defn- content-grid-update-entity! [ctx entity]
  (let [{:keys [grid cell-w cell-h]} (content-grid ctx)
        {::keys [content-cell] :as entity*} @entity
        [x y] (:position entity*)
        new-cell (get grid [(int (/ x cell-w))
                            (int (/ y cell-h))])]
    (when-not (= content-cell new-cell)
      (swap! new-cell update :entities conj entity)
      (swap! entity assoc ::content-cell new-cell)
      (when content-cell
        (swap! content-cell update :entities disj entity)))))

(defn- content-grid-remove-entity! [_ entity]
  (-> @entity
      ::content-cell
      (swap! update :entities disj entity)))

(defn- active-entities* [ctx center-entity*]
  (let [{:keys [grid]} (content-grid ctx)]
    (->> (let [idx (-> center-entity*
                       ::content-cell
                       deref
                       :idx)]
           (cons idx (g/get-8-neighbour-positions idx)))
         (keep grid)
         (mapcat (comp :entities deref)))))

(extend-type clojure.gdx.Context
  ActiveEntities
  (active-entities [ctx]
    (active-entities* ctx (player-entity* ctx))))

(defn ->content-grid [& {:keys [cell-size width height]}]
  {:grid (g/create-grid (inc (int (/ width cell-size))) ; inc because corners
                        (inc (int (/ height cell-size)))
                        (fn [idx]
                          (atom {:idx idx,
                                 :entities #{}})))
   :cell-w cell-size
   :cell-h cell-size})

(comment

 (defn get-all-entities-of-current-map [context]
   (mapcat (comp :entities deref)
           (g/cells (content-grid context))))

 (count
  (get-all-entities-of-current-map @app/state))

 )

