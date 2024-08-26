
# base
** :base/stat-effect ** ` [[effect-k operations] {:keys [effect/target]}] `
```
* :data nil
```


# effect
** :effect/convert ** ` [_ {:keys [effect/source effect/target]}] `
```
* :data :some
```

** :effect/damage ** ` [_ {:keys [effect/source effect/target]}] `
```
* :data [:map :damage/min-max]
```

** :effect/kill ** ` [_ {:keys [effect/target]}] `
```
* :data :some
```

** :effect/melee-damage ** ` [_ ctx] `
```
* :data :some
```

** :effect/projectile ** ` [[_ projectile-id] {:keys [effect/source effect/direction], :as ctx}] `
```
* :data [:qualified-keyword {:namespace :projectiles}]
```

** :effect/spawn ** ` [_ {:keys [effect/source effect/target-position]}] `
```
* :data [:qualified-keyword {:namespace :creatures}]
```

** :effect/stun ** ` [_ {:keys [effect/target]}] `
```
* :data :pos
```

** :effect/target-all ** ` [_ {:keys [effect/source effect/target], :as ctx}] `
```
* :data :some
```

** :effect/target-entity ** ` [_ {:keys [effect/source effect/target]}] `
```
* :data :some
```


# tx
** :tx/add-skill ** ` [[_ entity {:keys [property/id], :as skill}] _ctx] `
```
* :data nil
```

** :tx/add-text-effect ** ` [[_ entity text] ctx] `
```
* :data nil
```

** :tx/add-to-world ** ` [[_ entity] ctx] `
```
* :data nil
```

** :tx/apply-modifiers ** ` [[_ entity modifiers] _ctx] `
```
* :data nil
```

** :tx/create ** ` [[_ body components] ctx] `
```
* :data nil
```

** :tx/destroy ** ` [[_ entity] ctx] `
```
* :data nil
```

** :tx/effect ** ` [[_ effect-ctx effects] ctx] `
```
* :data nil
```

** :tx/event ** ` [[_ eid event params] ctx] `
```
* :data nil
```

** :tx/msg-to-player ** ` [[_ message] ctx] `
```
* :data nil
```

** :tx/pickup-item ** ` [[_ entity item] _ctx] `
```
* :data nil
```

** :tx/player-modal ** ` [[_ params] ctx] `
```
* :data nil
```

** :tx/position-changed ** ` [[_ entity] ctx] `
```
* :data nil
```

** :tx/remove-from-world ** ` [[_ entity] ctx] `
```
* :data nil
```

** :tx/remove-item ** ` [[_ entity cell] _ctx] `
```
* :data nil
```

** :tx/remove-item-from-widget ** ` [[_ cell] ctx] `
```
* :data nil
```

** :tx/remove-skill ** ` [[_ entity {:keys [property/id], :as skill}] _ctx] `
```
* :data nil
```

** :tx/reverse-modifiers ** ` [[_ entity modifiers] _ctx] `
```
* :data nil
```

** :tx/set-item ** ` [[_ entity cell item] _ctx] `
```
* :data nil
```

** :tx/set-item-image-in-widget ** ` [[_ cell item] ctx] `
```
* :data nil
```

** :tx/sound ** ` [_ ctx] `
```
* :data nil
```

** :tx/stack-item ** ` [[_ entity cell item] _ctx] `
```
* :data nil
```


# tx.context.action-bar
** :tx.context.action-bar/add-skill ** ` [[_ {:keys [property/id property/image], :as skill}] ctx] `
```
* :data nil
```

** :tx.context.action-bar/remove-skill ** ` [[_ {:keys [property/id]}] ctx] `
```
* :data nil
```


# tx.context.cursor
** :tx.context.cursor/set ** ` [[_ cursor-key] ctx] `
```
* :data nil
```


# tx.entity
** :tx.entity/assoc ** ` [[_ entity k v] ctx] `
```
* :data nil
```

** :tx.entity/assoc-in ** ` [[_ entity ks v] ctx] `
```
* :data nil
```

** :tx.entity/audiovisual ** ` [[_ position id] ctx] `
```
* :data nil
```

** :tx.entity/creature ** ` [[_ creature-id components] ctx] `
```
* :data nil
```

** :tx.entity/dissoc ** ` [[_ entity k] ctx] `
```
* :data nil
```

** :tx.entity/dissoc-in ** ` [[_ entity ks] ctx] `
```
* :data nil
```

** :tx.entity/item ** ` [[_ position item] _ctx] `
```
* :data nil
```

** :tx.entity/line-render ** ` [[_ {:keys [start end duration color thick?]}] _ctx] `
```
* :data nil
```

** :tx.entity/projectile ** ` [[_ projectile-id {:keys [position direction faction]}] ctx] `
```
* :data nil
```

** :tx.entity/set-movement ** ` [[_ entity movement] ctx] `
```
* :data nil
```

** :tx.entity/shout ** ` [[_ position faction delay-seconds] ctx] `
```
* :data nil
```

** :tx.entity/update-in ** ` [[_ entity ks f] ctx] `
```
* :data nil
```


# tx.entity.stats
** :tx.entity.stats/pay-mana-cost ** ` [[_ entity cost] _ctx] `
```
* :data nil
```

