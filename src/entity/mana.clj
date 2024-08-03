(ns entity.mana
  (:require [core.component :refer [defcomponent]]
            [api.entity :as entity]
            [core.data :as data]))

; required @ npc state, for cost, check if nil
(defcomponent :entity/mana data/nat-int-attr
  (entity/create-component [[_ max-mana] _components _ctx]
    [max-mana max-mana]))
