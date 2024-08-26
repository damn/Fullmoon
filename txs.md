
# base
## :base/stat-effect
```
* :data nil
* :params [[effect-k operations] {:keys [effect/target]}]
```


# effect
## :effect/convert
```
* :data :some
* :params [_ {:keys [effect/source effect/target]}]
```

## :effect/damage
```
* :data [:map :damage/min-max]
* :params [_ {:keys [effect/source effect/target]}]
```

## :effect/kill
```
* :data :some
* :params [_ {:keys [effect/target]}]
```

## :effect/melee-damage
```
* :data :some
* :params [_ ctx]
```

## :effect/projectile
```
* :data [:qualified-keyword {:namespace :projectiles}]
* :params [[_ projectile-id] {:keys [effect/source effect/direction], :as ctx}]
```

## :effect/spawn
```
* :data [:qualified-keyword {:namespace :creatures}]
* :params [_ {:keys [effect/source effect/target-position]}]
```

## :effect/stun
```
* :data :pos
* :params [_ {:keys [effect/target]}]
```

## :effect/target-all
```
* :data :some
* :params [_ {:keys [effect/source effect/target], :as ctx}]
```

## :effect/target-entity
```
* :data :some
* :params [_ {:keys [effect/source effect/target]}]
```


# tx
## :tx/add-skill
```
* :data nil
* :params [[_ entity {:keys [property/id], :as skill}] _ctx]
```

## :tx/add-text-effect
```
* :data nil
* :params [[_ entity text] ctx]
```

## :tx/add-to-world
```
* :data nil
* :params [[_ entity] ctx]
```

## :tx/apply-modifiers
```
* :data nil
* :params [[_ entity modifiers] _ctx]
```

## :tx/create
```
* :data nil
* :params [[_ body components] ctx]
```

## :tx/destroy
```
* :data nil
* :params [[_ entity] ctx]
```

## :tx/effect
```
* :data nil
* :params [[_ effect-ctx effects] ctx]
```

## :tx/event
```
* :data nil
* :params [[_ eid event params] ctx]
```

## :tx/msg-to-player
```
* :data nil
* :params [[_ message] ctx]
```

## :tx/pickup-item
```
* :data nil
* :params [[_ entity item] _ctx]
```

## :tx/player-modal
```
* :data nil
* :params [[_ params] ctx]
```

## :tx/position-changed
```
* :data nil
* :params [[_ entity] ctx]
```

## :tx/remove-from-world
```
* :data nil
* :params [[_ entity] ctx]
```

## :tx/remove-item
```
* :data nil
* :params [[_ entity cell] _ctx]
```

## :tx/remove-item-from-widget
```
* :data nil
* :params [[_ cell] ctx]
```

## :tx/remove-skill
```
* :data nil
* :params [[_ entity {:keys [property/id], :as skill}] _ctx]
```

## :tx/reverse-modifiers
```
* :data nil
* :params [[_ entity modifiers] _ctx]
```

## :tx/set-item
```
* :data nil
* :params [[_ entity cell item] _ctx]
```

## :tx/set-item-image-in-widget
```
* :data nil
* :params [[_ cell item] ctx]
```

## :tx/sound
```
* :data nil
* :params [_ ctx]
```

## :tx/stack-item
```
* :data nil
* :params [[_ entity cell item] _ctx]
```


# tx.context.action-bar
## :tx.context.action-bar/add-skill
```
* :data nil
* :params [[_ {:keys [property/id property/image], :as skill}] ctx]
```

## :tx.context.action-bar/remove-skill
```
* :data nil
* :params [[_ {:keys [property/id]}] ctx]
```


# tx.context.cursor
## :tx.context.cursor/set
```
* :data nil
* :params [[_ cursor-key] ctx]
```


# tx.entity
## :tx.entity/assoc
```
* :data nil
* :params [[_ entity k v] ctx]
```

## :tx.entity/assoc-in
```
* :data nil
* :params [[_ entity ks v] ctx]
```

## :tx.entity/audiovisual
```
* :data nil
* :params [[_ position id] ctx]
```

## :tx.entity/creature
```
* :data nil
* :params [[_ creature-id components] ctx]
```

## :tx.entity/dissoc
```
* :data nil
* :params [[_ entity k] ctx]
```

## :tx.entity/dissoc-in
```
* :data nil
* :params [[_ entity ks] ctx]
```

## :tx.entity/item
```
* :data nil
* :params [[_ position item] _ctx]
```

## :tx.entity/line-render
```
* :data nil
* :params [[_ {:keys [start end duration color thick?]}] _ctx]
```

## :tx.entity/projectile
```
* :data nil
* :params [[_ projectile-id {:keys [position direction faction]}] ctx]
```

## :tx.entity/set-movement
```
* :data nil
* :params [[_ entity movement] ctx]
```

## :tx.entity/shout
```
* :data nil
* :params [[_ position faction delay-seconds] ctx]
```

## :tx.entity/update-in
```
* :data nil
* :params [[_ entity ks f] ctx]
```


# tx.entity.stats
## :tx.entity.stats/pay-mana-cost
```
* :data nil
* :params [[_ entity cost] _ctx]
```

