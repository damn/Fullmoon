(ns cdq.entity.faction
  (:require [core.component :as component]
            [cdq.api.entity :as entity]
            [cdq.attributes :as attr]))

(component/def :entity/faction (attr/enum :good :evil))

(extend-type cdq.api.entity.Entity
  entity/Faction
  (enemy-faction [{:keys [entity/faction]}]
    (case faction
      :evil :good
      :good :evil))

  (friendly-faction [{:keys [entity/faction]}]
    faction))
