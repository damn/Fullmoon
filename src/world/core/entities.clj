(in-ns 'world.core)

(defn- init-ids->eids []
  (def ^:private ids->eids {}))

(defn all-entities [] (vals ids->eids))
(defn get-entity [id] (get ids->eids id))

; does not take into account zoom - but zoom is only for debug ???
; vision range?
(defn- on-screen? [entity]
  (let [[x y] (:position entity)
        x (float x)
        y (float y)
        [cx cy] (🎥/position (g/world-camera))
        px (float cx)
        py (float cy)
        xdist (Math/abs (- x px))
        ydist (Math/abs (- y py))]
    (and
     (<= xdist (inc (/ (float (g/world-viewport-width))  2)))
     (<= ydist (inc (/ (float (g/world-viewport-height)) 2))))))

; TODO at wrong point , this affects targeting logic of npcs
; move the debug flag to either render or mouseover or lets see
(def ^:private ^:dbg-flag los-checks? true)

; does not take into account size of entity ...
; => assert bodies <1 width then
(defn line-of-sight? [source target]
  (and (or (not (:entity/player? source))
           (on-screen? target))
       (not (and los-checks?
                 (ray-blocked? (:position source) (:position target))))))

(defn- remove-destroyed-entities!
  "Calls destroy on all entities which are marked with ':e/destroy'"
  []
  (mapcat (fn [eid]
            (cons [:tx/remove-from-world eid]
                  (for [component @eid]
                    #(entity/destroy component eid))))
          (filter (comp :entity/destroyed? deref) (all-entities))))

(let [cnt (atom 0)]
  (defn- unique-number! []
    (swap! cnt inc)))

(defn- create-vs
  "Creates a map for every component with map entries `[k (create [k v])]`."
  [components]
  (reduce (fn [m [k v]]
            (assoc m k (entity/->v [k v])))
          {}
          components))

(defc :e/create
  (tx/do! [[_ position body components]]
    (assert (and (not (contains? components :position))
                 (not (contains? components :entity/id))))
    (let [eid (atom (-> body
                        (assoc :position position)
                        entity/->Body
                        (safe-merge (-> components
                                        (assoc :entity/id (unique-number!))
                                        (create-vs)))))]
      (cons [:tx/add-to-world eid]
            (for [component @eid]
              #(entity/create component eid))))))

(defc :e/destroy
  (tx/do! [[_ eid]]
    [[:e/assoc eid :entity/destroyed? true]]))

(defc :e/assoc
  (tx/do! [[_ eid k v]]
    (assert (keyword? k))
    (swap! eid assoc k v)
    nil))

(defc :e/assoc-in
  (tx/do! [[_ eid ks v]]
    (swap! eid assoc-in ks v)
    nil))

(defc :e/dissoc
  (tx/do! [[_ eid k]]
    (assert (keyword? k))
    (swap! eid dissoc k)
    nil))

(defc :e/dissoc-in
  (tx/do! [[_ eid ks]]
    (assert (> (count ks) 1))
    (swap! eid update-in (drop-last ks) dissoc (last ks))
    nil))

(defc :e/update-in
  (tx/do! [[_ eid ks f]]
    (swap! eid update-in ks f)
    nil))

(def ^:private ^:dbg-flag show-body-bounds false)

(defn- draw-body-rect [entity color]
  (let [[x y] (:left-bottom entity)]
    (g/draw-rectangle x y (:width entity) (:height entity) color)))

(defn- render-entity! [system entity]
  (try
   (when show-body-bounds
     (draw-body-rect entity (if (:collides? entity) :white :gray)))
   (run! #(system % entity) entity)
   (catch Throwable t
     (draw-body-rect entity :red)
     (pretty-pst t 12))))

(defn- render-entities!
  "Draws entities in the correct z-order and in the order of render-systems for each z-order."
  [entities]
  (let [player-entity @player]
    (doseq [[z-order entities] (sort-by-order (group-by :z-order entities)
                                               first
                                               entity/render-order)
            system entity/render-systems
            entity entities
            :when (or (= z-order :z-order/effect)
                      (line-of-sight? player-entity entity))]
      (render-entity! system entity))))

; precaution in case a component gets removed by another component
; the question is do we still want to update nil components ?
; should be contains? check ?
; but then the 'order' is important? in such case dependent components
; should be moved together?
(defn- tick-system [eid]
  (try
   (doseq [k (keys @eid)]
     (when-let [v (k @eid)]
       (tx/do-all (entity/tick [k v] eid))))
   (catch Throwable t
     (throw (ex-info "" (select-keys @eid [:entity/id]) t)))))

(defn- tick-entities!
  "Calls tick system on all components of entities."
  [entities]
  (run! tick-system entities))
