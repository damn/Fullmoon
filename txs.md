
# base
__:base/stat-effect__ `[[effect-k operations] {:keys [effect/target]}]`
```
*` :data nil `
```
Descendants:
__:effect/movement-speed__ `data:  [:components :op/mult] `
__:effect/hp__ `data:  [:components :op/val-inc :op/val-mult :op/max-inc :op/max-mult] `
__:effect/mana__ `data:  [:components :op/val-inc :op/val-mult :op/max-inc :op/max-mult] `


# effect
__:effect/convert__ `[_ {:keys [effect/source effect/target]}]`
```
*` :data :some `
```

__:effect/damage__ `[_ {:keys [effect/source effect/target]}]`
```
*` :data [:map :damage/min-max] `
```

__:effect/kill__ `[_ {:keys [effect/target]}]`
```
*` :data :some `
```

__:effect/melee-damage__ `[_ ctx]`
```
*` :data :some `
```

__:effect/projectile__ `[[_ projectile-id] {:keys [effect/source effect/direction], :as ctx}]`
```
*` :data [:qualified-keyword {:namespace :projectiles}] `
```

__:effect/spawn__ `[_ {:keys [effect/source effect/target-position]}]`
```
*` :data [:qualified-keyword {:namespace :creatures}] `
```

__:effect/stun__ `[_ {:keys [effect/target]}]`
```
*` :data :pos `
```

__:effect/target-all__ `[_ {:keys [effect/source effect/target], :as ctx}]`
```
*` :data :some `
```

__:effect/target-entity__ `[_ {:keys [effect/source effect/target]}]`
```
*` :data :some `
```


# tx
__:tx/add-skill__ `[[_ entity {:keys [property/id], :as skill}] _ctx]`
```
*` :data nil `
```

__:tx/add-text-effect__ `[[_ entity text] ctx]`
```
*` :data nil `
```

__:tx/add-to-world__ `[[_ entity] ctx]`
```
*` :data nil `
```

__:tx/apply-modifiers__ `[[_ entity modifiers] _ctx]`
```
*` :data nil `
```

__:tx/create__ `[[_ body components] ctx]`
```
*` :data nil `
```

__:tx/destroy__ `[[_ entity] ctx]`
```
*` :data nil `
```

__:tx/effect__ `[[_ effect-ctx effects] ctx]`
```
*` :data nil `
```

__:tx/event__ `[[_ eid event params] ctx]`
```
*` :data nil `
```

__:tx/msg-to-player__ `[[_ message] ctx]`
```
*` :data nil `
```

__:tx/pickup-item__ `[[_ entity item] _ctx]`
```
*` :data nil `
```

__:tx/player-modal__ `[[_ params] ctx]`
```
*` :data nil `
```

__:tx/position-changed__ `[[_ entity] ctx]`
```
*` :data nil `
```

__:tx/remove-from-world__ `[[_ entity] ctx]`
```
*` :data nil `
```

__:tx/remove-item__ `[[_ entity cell] _ctx]`
```
*` :data nil `
```

__:tx/remove-item-from-widget__ `[[_ cell] ctx]`
```
*` :data nil `
```

__:tx/remove-skill__ `[[_ entity {:keys [property/id], :as skill}] _ctx]`
```
*` :data nil `
```

__:tx/reverse-modifiers__ `[[_ entity modifiers] _ctx]`
```
*` :data nil `
```

__:tx/set-item__ `[[_ entity cell item] _ctx]`
```
*` :data nil `
```

__:tx/set-item-image-in-widget__ `[[_ cell item] ctx]`
```
*` :data nil `
```

__:tx/sound__ `[_ ctx]`
```
*` :data nil `
```

__:tx/stack-item__ `[[_ entity cell item] _ctx]`
```
*` :data nil `
```


# tx.context.action-bar
__:tx.context.action-bar/add-skill__ `[[_ {:keys [property/id property/image], :as skill}] ctx]`
```
*` :data nil `
```

__:tx.context.action-bar/remove-skill__ `[[_ {:keys [property/id]}] ctx]`
```
*` :data nil `
```


# tx.context.cursor
__:tx.context.cursor/set__ `[[_ cursor-key] ctx]`
```
*` :data nil `
```


# tx.entity
__:tx.entity/assoc__ `[[_ entity k v] ctx]`
```
*` :data nil `
```

__:tx.entity/assoc-in__ `[[_ entity ks v] ctx]`
```
*` :data nil `
```

__:tx.entity/audiovisual__ `[[_ position id] ctx]`
```
*` :data nil `
```

__:tx.entity/creature__ `[[_ creature-id components] ctx]`
```
*` :data nil `
```

__:tx.entity/dissoc__ `[[_ entity k] ctx]`
```
*` :data nil `
```

__:tx.entity/dissoc-in__ `[[_ entity ks] ctx]`
```
*` :data nil `
```

__:tx.entity/item__ `[[_ position item] _ctx]`
```
*` :data nil `
```

__:tx.entity/line-render__ `[[_ {:keys [start end duration color thick?]}] _ctx]`
```
*` :data nil `
```

__:tx.entity/projectile__ `[[_ projectile-id {:keys [position direction faction]}] ctx]`
```
*` :data nil `
```

__:tx.entity/set-movement__ `[[_ entity movement] ctx]`
```
*` :data nil `
```

__:tx.entity/shout__ `[[_ position faction delay-seconds] ctx]`
```
*` :data nil `
```

__:tx.entity/update-in__ `[[_ entity ks f] ctx]`
```
*` :data nil `
```


# tx.entity.stats
__:tx.entity.stats/pay-mana-cost__ `[[_ entity cost] _ctx]`
```
*` :data nil `
```

