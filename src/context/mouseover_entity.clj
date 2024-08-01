(ns context.mouseover-entity
  (:require [utils.core :refer [sort-by-order]]
            [core.component :as component]
            [api.context :as ctx :refer [mouse-on-stage-actor? world-grid line-of-sight?]]
            [api.entity :as entity]
            [api.world.grid :refer [point->entities]]))

(defn- calculate-mouseover-entity [{:keys [context/player-entity] :as context}]
  (let [hits (filter #(:entity/z-order @%)
                     (point->entities (world-grid context)
                                      (ctx/world-mouse-position context)))]
    (->> entity/render-order
         (sort-by-order hits #(:entity/z-order @%))
         reverse
         (filter #(line-of-sight? context @player-entity @%))
         first)))

(component/def :context/mouseover-entity {}
  _
  (ctx/create [_ _ctx] (atom nil)))

(extend-type api.context.Context
  api.context/MouseOverEntity
  (update-mouseover-entity! [{:keys [context/mouseover-entity]
                              :as context}]
    (when-let [entity @mouseover-entity]
      (swap! entity dissoc :entity/mouseover?))
    (let [entity (if (mouse-on-stage-actor? context)
                   nil
                   (calculate-mouseover-entity context))]
      (reset! mouseover-entity entity)
      (when entity
        (swap! entity assoc :entity/mouseover? true)))))
