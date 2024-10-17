(in-ns 'world.core)

(def mouseover-eid nil)

(defn mouseover-entity []
  (when-let [eid mouseover-eid]
    @eid))

(defn- calculate-mouseover-eid []
  (let [player-entity @player
        hits (remove #(= (:z-order @%) :z-order/effect) ; or: only items/creatures/projectiles.
                     (point->entities (g/world-mouse-position)))]
    (->> entity/render-order
         (sort-by-order hits #(:z-order @%))
         reverse
         (filter #(line-of-sight? player-entity @%))
         first)))

(defn- update-mouseover-entity! []
  (let [eid (if (stage-screen/mouse-on-actor?)
              nil
              (calculate-mouseover-eid))]
    [(when mouseover-eid
       [:e/dissoc mouseover-eid :entity/mouseover?])
     (when eid
       [:e/assoc eid :entity/mouseover? true])
     (fn [] (.bindRoot #'mouseover-eid eid) nil)]))

(defc :entity/clickable
  (entity/render [[_ {:keys [text]}]
                  {:keys [entity/mouseover?] :as entity}]
    (when (and mouseover? text)
      (let [[x y] (:position entity)]
        (g/draw-text {:text text
                      :x x
                      :y (+ y (:half-height entity))
                      :up? true})))))

(def ^:private outline-alpha 0.4)
(def ^:private enemy-color    [1 0 0 outline-alpha])
(def ^:private friendly-color [0 1 0 outline-alpha])
(def ^:private neutral-color  [1 1 1 outline-alpha])

(defc :entity/mouseover?
  (entity/render-below [_ {:keys [entity/faction] :as entity}]
    (let [player-entity @player]
      (g/with-shape-line-width 3
        #(g/draw-ellipse (:position entity)
                         (:half-width entity)
                         (:half-height entity)
                         (cond (= faction (faction/enemy player-entity))
                               enemy-color
                               (= faction (faction/friend player-entity))
                               friendly-color
                               :else
                               neutral-color))))))
