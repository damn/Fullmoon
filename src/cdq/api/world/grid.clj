(ns cdq.api.world.grid)

(defprotocol Grid
  (cached-adjacent-cells [_ cell])
  (rectangle->cells [_ rectangle])
  (circle->cells    [_ circle])
  (circle->entities [_ circle])
  (point->entities [_ position])
  (valid-position? [_ entity*])
  (add-entity!              [_ entity])
  (remove-entity!           [_ entity])
  (entity-position-changed! [_ entity]))
