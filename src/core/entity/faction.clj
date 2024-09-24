(ns ^:no-doc core.entity.faction
  (:require [core.ctx :refer :all]
            [core.entity :as entity]))

(defcomponent :entity/faction
  {:let faction
   :data [:enum [:good :evil]]}
  (info-text [_ _ctx]
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
  {:data :some}
  (info-text [_ _effect-ctx]
    "Converts target to your side.")

  (applicable? [_ {:keys [effect/source effect/target]}]
    (and target
         (= (:entity/faction @target)
            (entity/enemy-faction @source))))

  (do! [_ {:keys [effect/source effect/target]}]
    [[:tx/audiovisual (:position @target) :audiovisuals/convert]
     [:e/assoc target :entity/faction (entity/friendly-faction @source)]]))
