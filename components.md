
# nil
* :height  nil
* :hit-effects  nil
* :maxrange  nil
* :solid?  nil
* :width  nil

# context
* :context/assets  {"create" [_ {:keys [folder sound-file-extensions image-file-extensions log-load-assets?]}]}
* :context/config  {"create" [_ {:keys [tag configs]}]}
* :context/graphics  {"create" [_ {:keys [views default-font cursors]}], "destroy" [_ {:keys [batch shape-drawer-texture default-font cursors]}]}
* :context/properties  {"create" [_ {:keys [file types]}]}
* :context/screens  {"create" [_ screens], "destroy" _}
* :context/tiled  {"create" _}
* :context/vis-ui  {"create" [_ skin-scale], "destroy" _}

# context.game.ecs
* :context.game.ecs/setup-entity  {"do!" [_ entity uid components]}

# creature
* :creature/entity  nil
* :creature/level  nil
* :creature/species  nil

# damage
* :damage/min-max  nil

# effect
* :effect/convert  {"text" _, "applicable?" _, "do!" _}
* :effect/damage  {"text" [_ damage], "applicable?" _, "do!" [_ damage]}
* :effect/hp -> :entity.stats/stat-effect nil
* :effect/kill  {"text" _, "applicable?" _, "do!" _}
* :effect/mana -> :entity.stats/stat-effect nil
* :effect/melee-damage  {"text" _, "applicable?" _, "do!" _}
* :effect/movement-speed -> :entity.stats/stat-effect nil
* :effect/projectile  {"text" [_ projectile-id], "applicable?" _, "useful?" [_ projectile-id], "do!" [_ projectile-id]}
* :effect/spawn  {"text" [_ creature-id], "applicable?" _, "do!" [_ creature-id]}
* :effect/stats-hp-set-to-max  {"text" _, "applicable?" _, "useful?" _, "do!" _}
* :effect/stats-mana-set-to-max  {"text" _, "applicable?" _, "useful?" _, "do!" _}
* :effect/stun  {"text" [_ duration], "applicable?" _, "do!" [_ duration]}
* :effect/target-entity  {"text" [_ {:keys [maxrange hit-effects]}], "applicable?" [_ {:keys [hit-effects]}], "useful?" [_ {:keys [maxrange]}], "do!" [_ {:keys [maxrange hit-effects]}], "render-info" [_ {:keys [maxrange]}]}

# entity
* :entity/animation  {"create" [_ animation], "tick" [k animation]}
* :entity/body  {"create-component" [_ {[x y] :position, :keys [position width height solid? z-order rotation-angle rotate-in-movement-direction? movement]}], "create" _, "destroy" _, "render-debug" [_ body], "tick" [_ body]}
* :entity/clickable  {"render-default" [_ {:keys [text]}]}
* :entity/delete-after-animation-stopped?  {"create" _, "tick" _}
* :entity/delete-after-duration  {"create-component" [_ duration], "tick" [_ counter], "info-text" [_ counter]}
* :entity/faction  {"info-text" [_ faction]}
* :entity/flying?  nil
* :entity/image  {"render-default" [_ image]}
* :entity/inventory  {"create" [_ item-ids]}
* :entity/line-render  {"render-default" [_ {:keys [thick? end color]}]}
* :entity/mouseover?  {"render-below" _}
* :entity/player?  {"create" _}
* :entity/plop  {"destroy" _}
* :entity/projectile-collision  {"create-component" [_ v], "info-text" [_ {:keys [hit-effects piercing?]}], "tick" [k {:keys [hit-effects already-hit-bodies piercing?]}]}
* :entity/reaction-time  nil
* :entity/shout  {"tick" [_ counter]}
* :entity/skills  {"create-component" [_ skill-ids], "info-text" [_ skills], "tick" [k skills]}
* :entity/state  {"create-component" [_ {:keys [fsm initial-state state-obj-constructors]}], "info-text" [_ state], "tick" [_ {:keys [state-obj]}], "render-below" [_ {:keys [state-obj]}], "render-above" [_ {:keys [state-obj]}], "render-info" [_ {:keys [state-obj]}]}
* :entity/stats  {"create-component" [_ stats], "info-text" [_ stats], "render-info" _}
* :entity/string-effect  {"tick" [k {:keys [counter]}], "render-above" [_ {:keys [text]}]}
* :entity/uid  {"create" _, "destroy" [_ uid]}

# entity.creature
* :entity.creature/name  {"info-text" [_ name]}
* :entity.creature/species  {"info-text" [_ species]}

# entity.stats
* :entity.stats/stat-effect  {"text" [k operations], "applicable?" [k _], "useful?" _, "do!" [effect-k operations]}

# item
* :item/modifiers  nil
* :item/slot  nil

# modifier
* :modifier/armor-pierce  nil
* :modifier/armor-save  nil
* :modifier/attack-speed  nil
* :modifier/cast-speed  nil
* :modifier/damage-deal  nil
* :modifier/damage-receive  nil
* :modifier/hp  nil
* :modifier/mana  nil
* :modifier/movement-speed  nil
* :modifier/strength  nil

# op
* :op/inc  {"operation-text" [_ value], "apply-operation" [_ value], "operation-order" _}
* :op/max-inc -> :op/val-max nil
* :op/max-mult -> :op/val-max nil
* :op/mult  {"operation-text" [_ value], "apply-operation" [_ value], "operation-order" _}
* :op/val-inc -> :op/val-max nil
* :op/val-max  {"operation-text" [op-k value], "apply-operation" [operation-k value], "operation-order" [op-k value]}
* :op/val-mult -> :op/val-max nil

# projectile
* :projectile/effects  nil
* :projectile/max-range  nil
* :projectile/piercing?  nil
* :projectile/speed  nil

# properties
* :properties/audiovisual  {"create" _}
* :properties/creature  {"create" _}
* :properties/item  {"create" _}
* :properties/projectile  {"create" _}
* :properties/skill  {"create" _}
* :properties/world  {"create" _}

# property
* :property/animation  nil
* :property/image  nil
* :property/pretty-name  nil
* :property/sound  nil

# screens
* :screens/game  {"create" _}
* :screens/main-menu  {"create" _}
* :screens/map-editor  {"create" _}
* :screens/minimap  {"create" _}
* :screens/options-menu  {"create" _}
* :screens/property-editor  {"create" _}

# skill
* :skill/action-time  nil
* :skill/action-time-modifier-key  nil
* :skill/cooldown  nil
* :skill/cost  nil
* :skill/effects  nil
* :skill/start-action-sound  nil

# stats
* :stats/armor-pierce  nil
* :stats/armor-save  nil
* :stats/attack-speed  nil
* :stats/cast-speed  nil
* :stats/hp  nil
* :stats/mana  nil
* :stats/modifiers  nil
* :stats/movement-speed  nil
* :stats/strength  nil

# tx
* :tx/add-skill  {"do!" [_ entity {:keys [property/id], :as skill}]}
* :tx/add-text-effect  {"do!" [_ entity text]}
* :tx/add-to-world  {"do!" [_ entity]}
* :tx/apply-modifiers  {"do!" [_ entity modifiers]}
* :tx/create  {"do!" [_ components]}
* :tx/destroy  {"do!" [_ entity]}
* :tx/effect  {"do!" [_ effect-ctx effects]}
* :tx/event  {"do!" [_ entity event params]}
* :tx/pickup-item  {"do!" [_ entity item]}

* :tx/msg-to-player  {"do!" [_ message]}
* :tx/player-modal  {"do!" [_ params]}
* :tx/sound  {"do!" [_ file]}
* :tx.context.cursor/set  {"do!" [_ cursor-key]}

* :tx/position-changed  {"do!" [_ entity]}
* :tx/remove-from-world  {"do!" [_ entity]}
* :tx/remove-item  {"do!" [_ entity cell]}
* :tx/remove-item-from-widget  {"do!" [_ cell]}
* :tx/remove-skill  {"do!" [_ entity {:keys [property/id], :as skill}]}
* :tx/reverse-modifiers  {"do!" [_ entity modifiers]}
* :tx/set-item  {"do!" [_ entity cell item]}
* :tx/set-item-image-in-widget  {"do!" [_ cell item]}
* :tx/stack-item  {"do!" [_ entity cell item]}

# tx.context.action-bar
* :tx.context.action-bar/add-skill  {"do!" [_ {:keys [property/id property/image], :as skill}]}
* :tx.context.action-bar/remove-skill  {"do!" [_ {:keys [property/id]}]}

# tx.context.cursor

# tx.entity
* :tx.entity/assoc  {"do!" [_ entity k v]}
* :tx.entity/assoc-in  {"do!" [_ entity ks v]}
* :tx.entity/audiovisual  {"do!" [_ position id]}
* :tx.entity/creature  {"do!" [_ creature-id components]}
* :tx.entity/dissoc  {"do!" [_ entity k]}
* :tx.entity/dissoc-in  {"do!" [_ entity ks]}
* :tx.entity/item  {"do!" [_ position item]}
* :tx.entity/line-render  {"do!" [_ {:keys [start end duration color thick?]}]}
* :tx.entity/projectile  {"do!" [_ projectile-id {:keys [position direction faction]}]}
* :tx.entity/set-movement  {"do!" [_ entity movement]}
* :tx.entity/shout  {"do!" [_ position faction delay-seconds]}
* :tx.entity/update-in  {"do!" [_ entity ks f]}

# tx.entity.stats
* :tx.entity.stats/pay-mana-cost  {"do!" [_ entity cost]}

# world
* :world/map-size  nil
* :world/max-area-level  nil
* :world/princess  nil
* :world/spawn-rate  nil
