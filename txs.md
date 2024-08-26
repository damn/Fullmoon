
# base
__ :base/stat-effect __ ` [[effect-k operations] {:keys [effect/target]}] `
```
* :data nil
```


# effect
__ :effect/convert __ ` [_ {:keys [effect/source effect/target]}] `
```
* :data :some
```

__ :effect/damage __ ` [_ {:keys [effect/source effect/target]}] `
```
* :data [:map :damage/min-max]
```

__ :effect/kill __ ` [_ {:keys [effect/target]}] `
```
* :data :some
```

__ :effect/melee-damage __ ` [_ ctx] `
```
* :data :some
```

__ :effect/projectile __ ` [[_ projectile-id] {:keys [effect/source effect/direction], :as ctx}] `
```
* :data [:qualified-keyword {:namespace :projectiles}]
```

__ :effect/spawn __ ` [_ {:keys [effect/source effect/target-position]}] `
```
* :data [:qualified-keyword {:namespace :creatures}]
```

__ :effect/stun __ ` [_ {:keys [effect/target]}] `
```
* :data :pos
```

__ :effect/target-all __ ` [_ {:keys [effect/source effect/target], :as ctx}] `
```
* :data :some
```

__ :effect/target-entity __ ` [_ {:keys [effect/source effect/target]}] `
```
* :data :some
```


# tx
__ :tx/add-skill __ ` [[_ entity {:keys [property/id], :as skill}] _ctx] `
```
* :data nil
```

__ :tx/add-text-effect __ ` [[_ entity text] ctx] `
```
* :data nil
```

__ :tx/add-to-world __ ` [[_ entity] ctx] `
```
* :data nil
```

__ :tx/apply-modifiers __ ` [[_ entity modifiers] _ctx] `
```
* :data nil
```

__ :tx/create __ ` [[_ body components] ctx] `
```
* :data nil
```

__ :tx/destroy __ ` [[_ entity] ctx] `
```
* :data nil
```

__ :tx/effect __ ` [[_ effect-ctx effects] ctx] `
```
* :data nil
```

__ :tx/event __ ` [[_ eid event params] ctx] `
```
* :data nil
```

__ :tx/msg-to-player __ ` [[_ message] ctx] `
```
* :data nil
```

__ :tx/pickup-item __ ` [[_ entity item] _ctx] `
```
* :data nil
```

__ :tx/player-modal __ ` [[_ params] ctx] `
```
* :data nil
```

__ :tx/position-changed __ ` [[_ entity] ctx] `
```
* :data nil
```

__ :tx/remove-from-world __ ` [[_ entity] ctx] `
```
* :data nil
```

__ :tx/remove-item __ ` [[_ entity cell] _ctx] `
```
* :data nil
```

__ :tx/remove-item-from-widget __ ` [[_ cell] ctx] `
```
* :data nil
```

__ :tx/remove-skill __ ` [[_ entity {:keys [property/id], :as skill}] _ctx] `
```
* :data nil
```

__ :tx/reverse-modifiers __ ` [[_ entity modifiers] _ctx] `
```
* :data nil
```

__ :tx/set-item __ ` [[_ entity cell item] _ctx] `
```
* :data nil
```

__ :tx/set-item-image-in-widget __ ` [[_ cell item] ctx] `
```
* :data nil
```

__ :tx/sound __ ` [_ ctx] `
```
* :data nil
```

__ :tx/stack-item __ ` [[_ entity cell item] _ctx] `
```
* :data nil
```


# tx.context.action-bar
__ :tx.context.action-bar/add-skill __ ` [[_ {:keys [property/id property/image], :as skill}] ctx] `
```
* :data nil
```

__ :tx.context.action-bar/remove-skill __ ` [[_ {:keys [property/id]}] ctx] `
```
* :data nil
```


# tx.context.cursor
__ :tx.context.cursor/set __ ` [[_ cursor-key] ctx] `
```
* :data nil
```


# tx.entity
__ :tx.entity/assoc __ ` [[_ entity k v] ctx] `
```
* :data nil
```

__ :tx.entity/assoc-in __ ` [[_ entity ks v] ctx] `
```
* :data nil
```

__ :tx.entity/audiovisual __ ` [[_ position id] ctx] `
```
* :data nil
```

__ :tx.entity/creature __ ` [[_ creature-id components] ctx] `
```
* :data nil
```

__ :tx.entity/dissoc __ ` [[_ entity k] ctx] `
```
* :data nil
```

__ :tx.entity/dissoc-in __ ` [[_ entity ks] ctx] `
```
* :data nil
```

__ :tx.entity/item __ ` [[_ position item] _ctx] `
```
* :data nil
```

__ :tx.entity/line-render __ ` [[_ {:keys [start end duration color thick?]}] _ctx] `
```
* :data nil
```

__ :tx.entity/projectile __ ` [[_ projectile-id {:keys [position direction faction]}] ctx] `
```
* :data nil
```

__ :tx.entity/set-movement __ ` [[_ entity movement] ctx] `
```
* :data nil
```

__ :tx.entity/shout __ ` [[_ position faction delay-seconds] ctx] `
```
* :data nil
```

__ :tx.entity/update-in __ ` [[_ entity ks f] ctx] `
```
* :data nil
```


# tx.entity.stats
__ :tx.entity.stats/pay-mana-cost __ ` [[_ entity cost] _ctx] `
```
* :data nil
```

