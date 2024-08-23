(ns entity.faction
  (:require [core.component :as component :refer [defcomponent]]
            [core.data :as data]
            [core.effect :as effect]
            [core.entity :as entity]))

(defcomponent :entity/faction (data/enum :good :evil)
  faction
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

(defcomponent :effect/convert data/boolean-attr
  (effect/text [_ _effect-ctx]
    "Converts target to your side.")

  (effect/applicable? [_ {:keys [effect/source effect/target]}]
    (and target
         (= (:entity/faction @target)
            (entity/enemy-faction @source))))

  (effect/do! [_ {:keys [effect/source effect/target]}]
    [[:tx.entity/audiovisual (:position @target) :audiovisuals/convert]
     [:tx.entity/assoc target :entity/faction (entity/friendly-faction @source)]]))
