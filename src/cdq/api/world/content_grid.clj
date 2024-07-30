(ns cdq.api.world.content-grid)

(defprotocol ContentGrid
  (update-entity! [_ entity])
  (remove-entity! [_ entity])
  (active-entities [_ center-entity]))

