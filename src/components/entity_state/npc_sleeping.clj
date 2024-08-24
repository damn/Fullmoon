(ns components.entity-state.npc-sleeping
  (:require [gdx.graphics.color :as color]
            [core.component :as component :refer [defcomponent]]
            [core.context :refer [world-grid]]
            [core.entity :as entity]
            [core.entity-state :as state]
            [core.graphics :as g]
            [core.world.cell :as cell]))

; TODO pass to creature data, also @ shout
(def ^:private aggro-range 6)

(defcomponent :npc-sleeping {}
  {:keys [eid]}
  (component/create [[_ eid] _ctx]
    {:eid eid})

  (state/exit [_ ctx]
    ; TODO make state = alerted, and shout at the end of that !
    ; then nice alert '!' and different entities different alert time
    [[:tx/add-text-effect eid "[WHITE]!"]
     [:tx.entity/shout (:position @eid) (:entity/faction @eid) 0.2]])

  (entity/tick [_ eid context]
    (let [entity* @eid
          cell ((world-grid context) (entity/tile entity*))]
      (when-let [distance (cell/nearest-entity-distance @cell (entity/enemy-faction entity*))]
        (when (<= distance aggro-range)
          [[:tx/event eid :alert]]))))

  (entity/render-above [_ entity* g _ctx]
    (let [[x y] (:position entity*)]
      (g/draw-text g
                   {:text "zzz"
                    :x x
                    :y (+ y (:half-height entity*))
                    :up? true}))))
