(in-ns 'world.core)

; Assumption: The map contains no not-allowed diagonal cells, diagonal wall cells where both
; adjacent cells are walls and blocked.
; (important for wavefront-expansion and field-following)
; * entities do not move to NADs (they remove them)
; * the potential field flows into diagonals, so they should be reachable too.
;
; TODO assert @ mapload no NAD's and @ potential field init & remove from
; potential-field-following the removal of NAD's.

(def ^:private pf-cache (atom nil))

(def ^:private factions-iterations {:good 15 :evil 5})

(defn pf-cell-blocked? [cell*]
  (blocked? cell* :z-order/ground))

; FIXME assert @ mapload no NAD's and @ potential field init & remove from
; potential-field-following the removal of NAD's.

; TODO remove max pot field movement player screen + 10 tiles as of screen size
; => is coupled to max-steps & also
; to friendly units follow player distance

; TODO remove cached get adj cells & use grid as atom not cells ?
; how to compare perfr ?

; TODO visualize steps, maybe I see something I missed

(comment
 (defrecord Foo [a b c])

 (let [^Foo foo (->Foo 1 2 3)]
   (time (dotimes [_ 10000000] (:a foo)))
   (time (dotimes [_ 10000000] (.a foo)))
   ; .a 7x faster ! => use for faction/distance & make record?
   ))

(comment
 ; Stepping through manually
 (clear-marked-cells! :good (get @faction->marked-cells :good))

 (defn- faction->tiles->entities-map* [entities]
   (into {}
         (for [[faction entities] (->> entities
                                       (filter   #(:entity/faction @%))
                                       (group-by #(:entity/faction @%)))]
           [faction
            (zipmap (map #(entity/tile @%) entities)
                    entities)])))

 (def max-iterations 1)

 (let [entities (map entity/get-entity [140 110 91])
       tl->es (:good (faction->tiles->entities-map* entities))]
   tl->es
   (def last-marked-cells (generate-potential-field :good tl->es)))
 (println *1)
 (def marked *2)
 (step :good *1)
 )

(defn- diagonal-cells? [cell* other-cell*]
  (let [[x1 y1] (:position cell*)
        [x2 y2] (:position other-cell*)]
    (and (not= x1 x2)
         (not= y1 y2))))

(defrecord FieldData [distance eid])

(defn- add-field-data! [cell faction distance eid]
  (swap! cell assoc faction (->FieldData distance eid)))

(defn- remove-field-data! [cell faction]
  (swap! cell assoc faction nil)) ; don't dissoc - will lose the Cell record type

; TODO performance
; * cached-adjacent-non-blocked-cells ? -> no need for cell blocked check?
; * sorted-set-by ?
; * do not refresh the potential-fields EVERY frame, maybe very 100ms & check for exists? target if they died inbetween.
; (or teleported?)
(defn- step [faction last-marked-cells]
  (let [marked-cells (transient [])
        distance       #(nearest-entity-distance % faction)
        nearest-entity #(nearest-entity          % faction)
        marked? faction]
    ; sorting important because of diagonal-cell values, flow from lower dist first for correct distance
    (doseq [cell (sort-by #(distance @%) last-marked-cells)
            adjacent-cell (cached-adjacent-cells cell)
            :let [cell* @cell
                  adjacent-cell* @adjacent-cell]
            :when (not (or (pf-cell-blocked? adjacent-cell*)
                           (marked? adjacent-cell*)))
            :let [distance-value (+ (float (distance cell*))
                                    (float (if (diagonal-cells? cell* adjacent-cell*)
                                             1.4 ; square root of 2 * 10
                                             1)))]]
      (add-field-data! adjacent-cell faction distance-value (nearest-entity cell*))
      (conj! marked-cells adjacent-cell))
    (persistent! marked-cells)))

(defn- generate-potential-field
  "returns the marked-cells"
  [faction tiles->entities max-iterations]
  (let [entity-cell-seq (for [[tile eid] tiles->entities] ; FIXME lazy seq
                          [eid (get grid tile)])
        marked (map second entity-cell-seq)]
    (doseq [[eid cell] entity-cell-seq]
      (add-field-data! cell faction 0 eid))
    (loop [marked-cells     marked
           new-marked-cells marked
           iterations 0]
      (if (= iterations max-iterations)
        marked-cells
        (let [new-marked (step faction new-marked-cells)]
          (recur (concat marked-cells new-marked) ; FIXME lazy seq
                 new-marked
                 (inc iterations)))))))

(defn- tiles->entities [entities faction]
  (let [entities (filter #(= (:entity/faction @%) faction)
                         entities)]
    (zipmap (map #(entity/tile @%) entities)
            entities)))

(defn- update-faction-potential-field [faction entities max-iterations]
  (let [tiles->entities (tiles->entities entities faction)
        last-state   [faction :tiles->entities]
        marked-cells [faction :marked-cells]]
    (when-not (= (get-in @pf-cache last-state) tiles->entities)
      (swap! pf-cache assoc-in last-state tiles->entities)
      (doseq [cell (get-in @pf-cache marked-cells)]
        (remove-field-data! cell faction))
      (swap! pf-cache assoc-in marked-cells (generate-potential-field
                                             faction
                                             tiles->entities
                                             max-iterations)))))

(defn- update-potential-fields! [entities]
  (doseq [[faction max-iterations] factions-iterations]
    (update-faction-potential-field faction entities max-iterations)))
