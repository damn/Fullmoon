(ns cdq.api.world.cell)

; is cell* actually
(defprotocol Cell
  (add-entity [_ entity])
  (remove-entity [_ entity])
  (add-occupying-entity [_ entity])
  (remove-occupying-entity [_ entity])
  (blocked? [_] [_ movement-type])
  (occupied-by-other? [_ entity]
                      "returns true if there is some occupying body with center-tile = this cell
                      or a multiple-cell-size body which touches this cell.")
  (nearest-entity          [_ faction])
  (nearest-entity-distance [_ faction]))

(defn cells->entities [cells*]
  (into #{} (mapcat :entities) cells*))
