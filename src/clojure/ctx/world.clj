(in-ns 'clojure.ctx)

(defprotocol Grid
  (cached-adjacent-cells [grid cell])
  (rectangle->cells [grid rectangle])
  (circle->cells    [grid circle])
  (circle->entities [grid circle]))

(defprotocol GridPointEntities
  (point->entities [ctx position]))

(defprotocol GridCell
  (blocked? [cell* z-order])
  (blocks-vision? [cell*])
  (occupied-by-other? [cell* entity]
                      "returns true if there is some occupying body with center-tile = this cell
                      or a multiple-cell-size body which touches this cell.")
  (nearest-entity          [cell* faction])
  (nearest-entity-distance [cell* faction]))

(defn cells->entities [cells*]
  (into #{} (mapcat :entities) cells*))

(defprotocol PRayCaster
  (ray-blocked? [ctx start target])
  (path-blocked? [ctx start target path-w] "path-w in tiles. casts two rays."))

(defprotocol Pathfinding
  (potential-fields-follow-to-enemy [ctx eid]))

