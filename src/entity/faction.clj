(ns entity.faction
  (:require [core.component :refer [defcomponent]]
            [core.data :as data]
            [api.effect :as effect]
            [api.entity :as entity]))

(defcomponent :entity/faction (data/enum :good :evil)
  (entity/info-text [[_ faction] _ctx]
    (str "[SLATE]Faction: " (name faction) "[]")))

(extend-type api.entity.Entity
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
