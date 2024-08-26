
# components.entity.stats
```
[:components.entity.stats/stat-effect [[effect-k operations] {:keys [effect/target]}]]
```

# effect
```
[:effect/convert [_ {:keys [effect/source effect/target]}]]
[:effect/damage [_ {:keys [effect/source effect/target]}]]
[:effect/kill [_ {:keys [effect/target]}]]
[:effect/melee-damage [_ ctx]]
[:effect/projectile [[_ projectile-id] {:keys [effect/source effect/direction], :as ctx}]]
[:effect/spawn [_ {:keys [effect/source effect/target-position]}]]
[:effect/stun [_ {:keys [effect/target]}]]
[:effect/target-all [_ {:keys [effect/source effect/target], :as ctx}]]
[:effect/target-entity [_ {:keys [effect/source effect/target]}]]
```

# tx
```
[:tx/add-skill [[_ entity {:keys [property/id], :as skill}] _ctx]]
[:tx/add-text-effect [[_ entity text] ctx]]
[:tx/add-to-world [[_ entity] ctx]]
[:tx/apply-modifiers [[_ entity modifiers] _ctx]]
[:tx/create [[_ body components] ctx]]
[:tx/destroy [[_ entity] ctx]]
[:tx/effect [[_ effect-ctx effects] ctx]]
[:tx/event [[_ eid event params] ctx]]
[:tx/msg-to-player [[_ message] ctx]]
[:tx/pickup-item [[_ entity item] _ctx]]
[:tx/player-modal [[_ params] ctx]]
[:tx/position-changed [[_ entity] ctx]]
[:tx/remove-from-world [[_ entity] ctx]]
[:tx/remove-item [[_ entity cell] _ctx]]
[:tx/remove-item-from-widget [[_ cell] ctx]]
[:tx/remove-skill [[_ entity {:keys [property/id], :as skill}] _ctx]]
[:tx/reverse-modifiers [[_ entity modifiers] _ctx]]
[:tx/set-item [[_ entity cell item] _ctx]]
[:tx/set-item-image-in-widget [[_ cell item] ctx]]
[:tx/sound [_ ctx]]
[:tx/stack-item [[_ entity cell item] _ctx]]
```

# tx.context.action-bar
```
[:tx.context.action-bar/add-skill [[_ {:keys [property/id property/image], :as skill}] ctx]]
[:tx.context.action-bar/remove-skill [[_ {:keys [property/id]}] ctx]]
```

# tx.context.cursor
```
[:tx.context.cursor/set [[_ cursor-key] ctx]]
```

# tx.entity
```
[:tx.entity/assoc [[_ entity k v] ctx]]
[:tx.entity/assoc-in [[_ entity ks v] ctx]]
[:tx.entity/audiovisual [[_ position id] ctx]]
[:tx.entity/creature [[_ creature-id components] ctx]]
[:tx.entity/dissoc [[_ entity k] ctx]]
[:tx.entity/dissoc-in [[_ entity ks] ctx]]
[:tx.entity/item [[_ position item] _ctx]]
[:tx.entity/line-render [[_ {:keys [start end duration color thick?]}] _ctx]]
[:tx.entity/projectile [[_ projectile-id {:keys [position direction faction]}] ctx]]
[:tx.entity/set-movement [[_ entity movement] ctx]]
[:tx.entity/shout [[_ position faction delay-seconds] ctx]]
[:tx.entity/update-in [[_ entity ks f] ctx]]
```

# tx.entity.stats
```
[:tx.entity.stats/pay-mana-cost [[_ entity cost] _ctx]]
```
