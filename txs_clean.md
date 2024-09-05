
# base
* __:base/stat-effect__ `[[effect-k operations] {:keys [effect/target]}]`
    * Descendants
      * :effect.entity/movement-speed
        * data: `[:components [:op/mult]]`
      * :effect.entity/mana
        * data: `[:components [:op/val-inc :op/val-mult :op/max-inc :op/max-mult]]`
      * :effect.entity/hp
        * data: `[:components [:op/val-inc :op/val-mult :op/max-inc :op/max-mult]]`

# effect
* __:effect/projectile__ `[_ {:keys [effect/source effect/direction], :as ctx}]`
    * data: `[:one-to-one :properties/projectile]`
* __:effect/spawn__ `[_ {:keys [effect/source effect/target-position]}]`
    * data: `[:one-to-one :properties/creature]`
* __:effect/target-all__ `[_ {:keys [effect/source effect/target], :as ctx}]`
    * data: `[:map [:entity-effects]]`
* __:effect/target-entity__ `[_ {:keys [effect/source effect/target]}]`
    * data: `[:map [:entity-effects :maxrange]]`

# effect.entity
* __:effect.entity/convert__ `[_ {:keys [effect/source effect/target]}]`
    * data: `:some`
* __:effect.entity/damage__ `[_ {:keys [effect/source effect/target]}]`
    * data: `[:map [:damage/min-max]]`
* __:effect.entity/kill__ `[_ {:keys [effect/target]}]`
    * data: `:some`
* __:effect.entity/melee-damage__ `[_ ctx]`
    * data: `:some`
* __:effect.entity/spiderweb__ `[_ {:keys [effect/source effect/target], :as ctx}]`
    * data: `:some`
* __:effect.entity/stun__ `[_ {:keys [effect/target]}]`
    * data: `:pos`

# tx
* __:tx/add-skill__ `[[_ entity {:keys [property/id], :as skill}] _ctx]`
* __:tx/remove-skill__ `[[_ entity {:keys [property/id], :as skill}] _ctx]`

* __:tx/add-text-effect__ `[[_ entity text] ctx]`

* __:tx/add-to-world__ `[[_ entity] ctx]`
* __:tx/remove-from-world__ `[[_ entity] ctx]`
* __:tx/position-changed__ `[[_ entity] ctx]`

* __:tx/apply-modifiers__ `[[_ entity modifiers] _ctx]`
* __:tx/reverse-modifiers__ `[[_ entity modifiers] _ctx]`

* __:tx/sound__ `[_ ctx]`
    * data: `:sound`

* __:tx/msg-to-player__ `[[_ message] ctx]`
* __:tx/player-modal__ `[[_ params] ctx]`

* __:tx/create__ `[[_ position body components] ctx]`

* __:tx/audiovisual__ `[[_ position id] ctx]`
* __:tx/creature__ `[_ ctx]`
* __:tx/item__ `[[_ position item] _ctx]`
* __:tx/line-render__ `[[_ {:keys [start end duration color thick?]}] _ctx]`
* __:tx/projectile__ `[[_ {:keys [position direction faction]} {:keys [entity/image projectile/max-range projectile/speed entity-effects projectile/piercing?], :as projectile}] ctx]`
* __:tx/shout__ `[[_ position faction delay-seconds] ctx]`

* __:tx/destroy__ `[[_ entity] ctx]`
* __:tx/assoc__ `[[_ entity k v] ctx]`
* __:tx/assoc-in__ `[[_ entity ks v] ctx]`
* __:tx/dissoc__ `[[_ entity k] ctx]`
* __:tx/dissoc-in__ `[[_ entity ks] ctx]`
* __:tx/update-in__ `[[_ entity ks f] ctx]`

* __:tx/effect__ `[[_ effect-ctx effects] ctx]`

* __:tx/event__ `[[_ eid event params] ctx]`

* __:tx/set-movement__ `[[_ entity movement] ctx]`

* __:tx/remove-item-from-widget__ `[[_ cell] ctx]`
* __:tx/set-item-image-in-widget__ `[[_ cell item] ctx]`

* __:tx/pickup-item__ `[[_ entity item] _ctx]`
* __:tx/remove-item__ `[[_ entity cell] _ctx]`
* __:tx/set-item__ `[[_ entity cell item] _ctx]`
* __:tx/stack-item__ `[[_ entity cell item] _ctx]`

# tx.context.action-bar
* __:tx.context.action-bar/add-skill__ `[[_ {:keys [property/id entity/image], :as skill}] ctx]`
* __:tx.context.action-bar/remove-skill__ `[[_ {:keys [property/id]}] ctx]`

# tx.context.cursor
* __:tx.context.cursor/set__ `[[_ cursor-key] ctx]`

# tx.entity.stats
* __:tx.entity.stats/pay-mana-cost__ `[[_ entity cost] _ctx]`
