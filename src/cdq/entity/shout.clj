(ns cdq.entity.shout
  (:require [core.component :as component]
            [cdq.api.context :refer [world-grid line-of-sight? stopped?]]
            [cdq.api.entity :as entity]
            [cdq.api.world.grid :refer [circle->entities]]))

(def ^:private shout-range 6)

; TODO gets itself also
  ; == faction/friendly? e1 e2 ( entity*/friendly? e*1 e*2) ?
(defn- get-friendly-entities-in-line-of-sight [context entity* radius]
  (->> {:position (:entity/position entity*)
        :radius radius}
       (circle->entities (world-grid context))
       (map deref)
       (filter #(and (= (:entity/faction %) (:entity/faction entity*))
                     (line-of-sight? context entity* %)))))

(component/def :entity/shout {}
  counter
  (entity/tick [_ {:keys [entity/id] :as entity*} context]
    (when (stopped? context counter)
      (cons [:tx/destroy id]
            (for [{:keys [entity/id]} (get-friendly-entities-in-line-of-sight context entity* shout-range)]
              [:tx/event id :alert])))))
