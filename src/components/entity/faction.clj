(ns components.entity.faction
  (:require [core.component :as component :refer [defcomponent]]
            [core.entity :as entity]))

(defcomponent :entity/faction
  {:let faction
   :data [:enum [:good :evil]]}
  (component/info-text [_ _ctx]
    (str "[SLATE]Faction: " (name faction) "[]")))

(extend-type core.entity.Entity
  entity/Faction
  (enemy-faction [{:keys [entity/faction]}]
    (case faction
      :evil :good
      :good :evil))

  (friendly-faction [{:keys [entity/faction]}]
    faction))

(defcomponent :effect.entity/convert
  {:data :some
   :default-value true}
  (component/info-text [_ _effect-ctx]
    "Converts target to your side.")

  (component/applicable? [_ {:keys [effect/source effect/target]}]
    (and target
         (= (:entity/faction @target)
            (entity/enemy-faction @source))))

  (component/do! [_ {:keys [effect/source effect/target]}]
    [[:tx/audiovisual (:position @target) :audiovisuals/convert]
     [:tx/assoc target :entity/faction (entity/friendly-faction @source)]]))
