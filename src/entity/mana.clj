(ns entity.mana
  (:require [core.component :as component]
            [api.entity :as entity]
            [data.types :as attr]))

; required @ npc state, for cost, check if nil
(component/def :entity/mana attr/nat-int-attr
  max-mana
  (entity/create-component [_ _components _ctx]
    [max-mana max-mana]))
