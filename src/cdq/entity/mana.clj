(ns cdq.entity.mana
  (:require [core.component :as component]
            [cdq.api.entity :as entity]
            [cdq.attributes :as attr]))

; required @ npc state, for cost, check if nil
(component/def :entity/mana attr/nat-int-attr
  max-mana
  (entity/create-component [_ _components _ctx]
    [max-mana max-mana]))
