
# nil
* :height  {:widget :label, :schema #object[clojure.core$pos_QMARK_ 0x4155a965 "clojure.core$pos_QMARK_@4155a965"]}
* :hit-effects  {:widget :nested-map, :schema [:map {:closed true} [:effect/damage # #] [:effect/stats-hp-set-to-max # #] [:effect/movement-speed # #] [:effect/hp # #] [:effect/convert # :boolean] [:effect/melee-damage # :some] [:effect/stats-mana-set-to-max # #] [:effect/mana # #]], :components (:effect/damage :effect/stats-hp-set-to-max :effect/movement-speed :effect/hp :effect/convert :effect/melee-damage :effect/stats-mana-set-to-max :effect/mana)}
* :maxrange  {:widget :text-field, :schema #object[clojure.core$pos_QMARK_ 0x4155a965 "clojure.core$pos_QMARK_@4155a965"]}
* :solid?  {:widget :label, :schema #object[clojure.core$boolean_QMARK_ 0x67205ec8 "clojure.core$boolean_QMARK_@67205ec8"]}
* :width  {:widget :label, :schema #object[clojure.core$pos_QMARK_ 0x4155a965 "clojure.core$pos_QMARK_@4155a965"]}

# context
* :context/assets  
  *  core.component/create
* :context/config  
  *  core.component/create
* :context/graphics  
  *  core.component/destroy
  *  core.component/create
* :context/properties  
  *  core.component/create
* :context/screens  
  *  core.component/destroy
  *  core.component/create
* :context/tiled  
  *  core.component/create
* :context/vis-ui  
  *  core.component/destroy
  *  core.component/create

# creature
* :creature/entity  {:widget :nested-map, :schema [:map {:closed true} [:entity/animation # :some] [:entity/body # #] [:entity/flying? # :boolean] [:entity/reaction-time # #object[clojure.core$pos_QMARK_ 0x4155a965 "clojure.core$pos_QMARK_@4155a965"]] [:entity/faction # #] [:entity/stats # #] [:entity/inventory # #] [:entity/skills # #]], :components [:entity/animation :entity/body :entity/flying? :entity/reaction-time :entity/faction :entity/stats :entity/inventory :entity/skills]}
* :creature/level  {:widget :text-field, :schema [:maybe #object[clojure.core$pos_int_QMARK_ 0x3840b4e6 "clojure.core$pos_int_QMARK_@3840b4e6"]]}
* :creature/species  {:widget :label, :schema [:qualified-keyword {:namespace :species}]}

# damage
* :damage/min-max  {:widget :text-field, :schema [:and [:vector # #] [:fn # #object[data.val_max$fn__16774 0x29c7e28c "data.val_max$fn__16774@29c7e28c"]]]}

# effect
* :effect/convert  {:widget :check-box, :schema :boolean, :default-value true}
  *  api.effect/text
  *  api.tx/transact!
  *  api.effect/applicable?
* :effect/damage  {:widget :nested-map, :schema [:map {:closed true} [:damage/min-max #]]}
  *  api.effect/text
  *  api.tx/transact!
  *  api.effect/applicable?
* :effect/hp -> :entity.stats/stat-effect {:widget :nested-map, :schema [:map {:closed true} [:op/val-inc # #object[clojure.core$int_QMARK_ 0x2d0d357b "clojure.core$int_QMARK_@2d0d357b"]] [:op/val-mult # #object[clojure.core$number_QMARK_ 0x23bc053c "clojure.core$number_QMARK_@23bc053c"]] [:op/max-inc # #object[clojure.core$int_QMARK_ 0x2d0d357b "clojure.core$int_QMARK_@2d0d357b"]] [:op/max-mult # #object[clojure.core$number_QMARK_ 0x23bc053c "clojure.core$number_QMARK_@23bc053c"]]], :components [:op/val-inc :op/val-mult :op/max-inc :op/max-mult]}
* :effect/kill  {:widget :check-box, :schema :boolean, :default-value true}
  *  api.effect/text
  *  api.tx/transact!
  *  api.effect/applicable?
* :effect/mana -> :entity.stats/stat-effect {:widget :nested-map, :schema [:map {:closed true} [:op/val-inc # #object[clojure.core$int_QMARK_ 0x2d0d357b "clojure.core$int_QMARK_@2d0d357b"]] [:op/val-mult # #object[clojure.core$number_QMARK_ 0x23bc053c "clojure.core$number_QMARK_@23bc053c"]] [:op/max-inc # #object[clojure.core$int_QMARK_ 0x2d0d357b "clojure.core$int_QMARK_@2d0d357b"]] [:op/max-mult # #object[clojure.core$number_QMARK_ 0x23bc053c "clojure.core$number_QMARK_@23bc053c"]]], :components [:op/val-inc :op/val-mult :op/max-inc :op/max-mult]}
* :effect/melee-damage  
  *  api.effect/text
  *  api.tx/transact!
  *  api.effect/applicable?
* :effect/movement-speed -> :entity.stats/stat-effect {:widget :nested-map, :schema [:map {:closed true} [:op/mult # #object[clojure.core$number_QMARK_ 0x23bc053c "clojure.core$number_QMARK_@23bc053c"]]], :components [:op/mult]}
* :effect/projectile  {:widget :text-field, :schema [:qualified-keyword {:namespace :projectiles}]}
  *  api.effect/text
  *  api.effect/useful?
  *  api.tx/transact!
  *  api.effect/applicable?
* :effect/spawn  {:widget :text-field, :schema [:qualified-keyword {:namespace :creatures}]}
  *  api.effect/text
  *  api.tx/transact!
  *  api.effect/applicable?
* :effect/stats-hp-set-to-max  {:schema [:= true], :default-value true, :widget :label}
  *  api.effect/text
  *  api.effect/useful?
  *  api.tx/transact!
  *  api.effect/applicable?
* :effect/stats-mana-set-to-max  {:schema [:= true], :default-value true, :widget :label}
  *  api.effect/text
  *  api.effect/useful?
  *  api.tx/transact!
  *  api.effect/applicable?
* :effect/stun  {:widget :text-field, :schema #object[clojure.core$pos_QMARK_ 0x4155a965 "clojure.core$pos_QMARK_@4155a965"]}
  *  api.effect/text
  *  api.tx/transact!
  *  api.effect/applicable?
* :effect/target-entity  {:widget :nested-map, :schema [:map {:closed true} [:hit-effects #] [:maxrange #object[clojure.core$pos_QMARK_ 0x4155a965 "clojure.core$pos_QMARK_@4155a965"]]], :default-value {:hit-effects {}, :max-range 2.0}, :doc "Applies hit-effects to a target if they are inside max-range & in line of sight.\nCancels if line of sight is lost. Draws a red/yellow line wheter the target is inside the max range. If the effect is to be done and target out of range -> draws a hit-ground-effect on the max location."}
  *  api.effect/text
  *  api.effect/render-info
  *  api.effect/useful?
  *  api.tx/transact!
  *  api.effect/applicable?

# entity
* :entity/animation  {:widget :animation, :schema :some}
  *  api.entity/create
  *  api.entity/tick
* :entity/body  {:widget :nested-map, :schema [:map {:closed true} [:width #object[clojure.core$pos_QMARK_ 0x4155a965 "clojure.core$pos_QMARK_@4155a965"]] [:height #object[clojure.core$pos_QMARK_ 0x4155a965 "clojure.core$pos_QMARK_@4155a965"]] [:solid? #object[clojure.core$boolean_QMARK_ 0x67205ec8 "clojure.core$boolean_QMARK_@67205ec8"]]]}
  *  api.entity/destroy
  *  api.entity/create
  *  api.entity/create-component
  *  api.entity/render-debug
  *  api.entity/tick
* :entity/clickable  
  *  api.entity/render-default
* :entity/delete-after-animation-stopped?  
  *  api.entity/create
  *  api.entity/tick
* :entity/delete-after-duration  
  *  api.entity/create-component
  *  api.entity/info-text
  *  api.entity/tick
* :entity/faction  {:widget :enum, :schema [:enum :good :evil], :items (:good :evil)}
  *  api.entity/info-text
* :entity/flying?  {:widget :check-box, :schema :boolean, :default-value true}
* :entity/image  
  *  api.entity/render-default
* :entity/inventory  {:widget :one-to-many, :schema [:set :qualified-keyword], :linked-property-type :properties/item}
  *  api.entity/create
* :entity/line-render  
  *  api.entity/render-default
* :entity/mouseover?  
  *  api.entity/render-below
* :entity/player?  
  *  api.entity/create
* :entity/plop  
  *  api.entity/destroy
* :entity/projectile-collision  
  *  api.entity/create-component
  *  api.entity/info-text
  *  api.entity/tick
* :entity/reaction-time  {:widget :text-field, :schema #object[clojure.core$pos_QMARK_ 0x4155a965 "clojure.core$pos_QMARK_@4155a965"]}
* :entity/shout  
  *  api.entity/tick
* :entity/skills  {:widget :one-to-many, :schema [:set :qualified-keyword], :linked-property-type :properties/skill}
  *  api.entity/create-component
  *  api.entity/info-text
  *  api.entity/tick
* :entity/state  
  *  api.entity/create-component
  *  api.entity/render-above
  *  api.entity/render-below
  *  api.entity/info-text
  *  api.entity/tick
  *  api.entity/render-info
* :entity/stats  {:widget :nested-map, :schema [:map {:closed true} [:stats/movement-speed # #] [:stats/armor-pierce # #object[clojure.core$number_QMARK_ 0x23bc053c "clojure.core$number_QMARK_@23bc053c"]] [:stats/mana # #object[clojure.core$nat_int_QMARK_ 0x5b7f245 "clojure.core$nat_int_QMARK_@5b7f245"]] [:stats/modifiers # #] [:stats/strength # #object[clojure.core$nat_int_QMARK_ 0x5b7f245 "clojure.core$nat_int_QMARK_@5b7f245"]] [:stats/cast-speed # #object[clojure.core$pos_QMARK_ 0x4155a965 "clojure.core$pos_QMARK_@4155a965"]] [:stats/hp # #object[clojure.core$pos_int_QMARK_ 0x3840b4e6 "clojure.core$pos_int_QMARK_@3840b4e6"]] [:stats/armor-save # #object[clojure.core$number_QMARK_ 0x23bc053c "clojure.core$number_QMARK_@23bc053c"]] [:stats/attack-speed # #object[clojure.core$pos_QMARK_ 0x4155a965 "clojure.core$pos_QMARK_@4155a965"]]], :components (:stats/movement-speed :stats/armor-pierce :stats/mana :stats/modifiers :stats/strength :stats/cast-speed :stats/hp :stats/armor-save :stats/attack-speed)}
  *  api.entity/create-component
  *  api.entity/info-text
  *  api.entity/render-info
* :entity/string-effect  
  *  api.entity/render-above
  *  api.entity/tick
* :entity/uid  
  *  api.entity/destroy
  *  api.entity/create

# entity.creature
* :entity.creature/name  
  *  api.entity/info-text
* :entity.creature/species  
  *  api.entity/info-text

# entity.stats
* :entity.stats/stat-effect  
  *  api.effect/text
  *  api.effect/useful?
  *  api.tx/transact!
  *  api.effect/applicable?

# item
* :item/modifiers  {:widget :nested-map, :schema [:map {:closed true} [:modifier/armor-save # #] [:modifier/armor-pierce # #] [:modifier/attack-speed # #] [:modifier/strength # #] [:modifier/cast-speed # #] [:modifier/movement-speed # #] [:modifier/hp # #] [:modifier/mana # #] [:modifier/damage-deal # #] [:modifier/damage-receive # #]], :components (:modifier/armor-save :modifier/armor-pierce :modifier/attack-speed :modifier/strength :modifier/cast-speed :modifier/movement-speed :modifier/hp :modifier/mana :modifier/damage-deal :modifier/damage-receive)}
* :item/slot  {:widget :label, :schema [:qualified-keyword {:namespace :inventory.slot}]}

# modifier
* :modifier/armor-pierce  {:widget :nested-map, :schema [:map {:closed true} [:op/inc # #object[clojure.core$number_QMARK_ 0x23bc053c "clojure.core$number_QMARK_@23bc053c"]]], :components [:op/inc]}
* :modifier/armor-save  {:widget :nested-map, :schema [:map {:closed true} [:op/inc # #object[clojure.core$number_QMARK_ 0x23bc053c "clojure.core$number_QMARK_@23bc053c"]]], :components [:op/inc]}
* :modifier/attack-speed  {:widget :nested-map, :schema [:map {:closed true} [:op/inc # #object[clojure.core$number_QMARK_ 0x23bc053c "clojure.core$number_QMARK_@23bc053c"]]], :components [:op/inc]}
* :modifier/cast-speed  {:widget :nested-map, :schema [:map {:closed true} [:op/inc # #object[clojure.core$number_QMARK_ 0x23bc053c "clojure.core$number_QMARK_@23bc053c"]]], :components [:op/inc]}
* :modifier/damage-deal  {:widget :nested-map, :schema [:map {:closed true} [:op/val-inc # #object[clojure.core$int_QMARK_ 0x2d0d357b "clojure.core$int_QMARK_@2d0d357b"]] [:op/val-mult # #object[clojure.core$number_QMARK_ 0x23bc053c "clojure.core$number_QMARK_@23bc053c"]] [:op/max-inc # #object[clojure.core$int_QMARK_ 0x2d0d357b "clojure.core$int_QMARK_@2d0d357b"]] [:op/max-mult # #object[clojure.core$number_QMARK_ 0x23bc053c "clojure.core$number_QMARK_@23bc053c"]]], :components [:op/val-inc :op/val-mult :op/max-inc :op/max-mult]}
* :modifier/damage-receive  {:widget :nested-map, :schema [:map {:closed true} [:op/inc # #object[clojure.core$number_QMARK_ 0x23bc053c "clojure.core$number_QMARK_@23bc053c"]] [:op/mult # #object[clojure.core$number_QMARK_ 0x23bc053c "clojure.core$number_QMARK_@23bc053c"]]], :components [:op/inc :op/mult]}
* :modifier/hp  {:widget :nested-map, :schema [:map {:closed true} [:op/max-inc # #object[clojure.core$int_QMARK_ 0x2d0d357b "clojure.core$int_QMARK_@2d0d357b"]] [:op/max-mult # #object[clojure.core$number_QMARK_ 0x23bc053c "clojure.core$number_QMARK_@23bc053c"]]], :components [:op/max-inc :op/max-mult]}
* :modifier/mana  {:widget :nested-map, :schema [:map {:closed true} [:op/max-inc # #object[clojure.core$int_QMARK_ 0x2d0d357b "clojure.core$int_QMARK_@2d0d357b"]] [:op/max-mult # #object[clojure.core$number_QMARK_ 0x23bc053c "clojure.core$number_QMARK_@23bc053c"]]], :components [:op/max-inc :op/max-mult]}
* :modifier/movement-speed  {:widget :nested-map, :schema [:map {:closed true} [:op/inc # #object[clojure.core$number_QMARK_ 0x23bc053c "clojure.core$number_QMARK_@23bc053c"]] [:op/mult # #object[clojure.core$number_QMARK_ 0x23bc053c "clojure.core$number_QMARK_@23bc053c"]]], :components [:op/inc :op/mult]}
* :modifier/strength  {:widget :nested-map, :schema [:map {:closed true} [:op/inc # #object[clojure.core$number_QMARK_ 0x23bc053c "clojure.core$number_QMARK_@23bc053c"]]], :components [:op/inc]}

# op
* :op/inc  {:widget :text-field, :schema #object[clojure.core$number_QMARK_ 0x23bc053c "clojure.core$number_QMARK_@23bc053c"]}
  *  entity.stats/operation-text
  *  entity.stats/operation-order
  *  entity.stats/apply-operation
* :op/max-inc -> :op/val-max {:widget :text-field, :schema #object[clojure.core$int_QMARK_ 0x2d0d357b "clojure.core$int_QMARK_@2d0d357b"]}
* :op/max-mult -> :op/val-max {:widget :text-field, :schema #object[clojure.core$number_QMARK_ 0x23bc053c "clojure.core$number_QMARK_@23bc053c"]}
* :op/mult  {:widget :text-field, :schema #object[clojure.core$number_QMARK_ 0x23bc053c "clojure.core$number_QMARK_@23bc053c"]}
  *  entity.stats/operation-text
  *  entity.stats/operation-order
  *  entity.stats/apply-operation
* :op/val-inc -> :op/val-max {:widget :text-field, :schema #object[clojure.core$int_QMARK_ 0x2d0d357b "clojure.core$int_QMARK_@2d0d357b"]}
* :op/val-max  
  *  entity.stats/operation-text
  *  entity.stats/operation-order
  *  entity.stats/apply-operation
* :op/val-mult -> :op/val-max {:widget :text-field, :schema #object[clojure.core$number_QMARK_ 0x23bc053c "clojure.core$number_QMARK_@23bc053c"]}

# projectile
* :projectile/effects  {:widget :nested-map, :schema [:map {:closed true} [:effect/kill # :boolean] [:effect/damage # #] [:effect/projectile # #] [:effect/stats-hp-set-to-max # #] [:effect/movement-speed # #] [:effect/hp # #] [:effect/stun # #object[clojure.core$pos_QMARK_ 0x4155a965 "clojure.core$pos_QMARK_@4155a965"]] [:effect/convert # :boolean] [:effect/spawn # #] [:effect/melee-damage # :some] [:effect/stats-mana-set-to-max # #] [:effect/target-entity # #] [:effect/mana # #]], :components (:effect/kill :effect/damage :effect/projectile :effect/stats-hp-set-to-max :effect/movement-speed :effect/hp :effect/stun :effect/convert :effect/spawn :effect/melee-damage :effect/stats-mana-set-to-max :effect/target-entity :effect/mana)}
* :projectile/max-range  {:widget :text-field, :schema #object[clojure.core$pos_int_QMARK_ 0x3840b4e6 "clojure.core$pos_int_QMARK_@3840b4e6"]}
* :projectile/piercing?  {:widget :check-box, :schema :boolean, :default-value true}
* :projectile/speed  {:widget :text-field, :schema #object[clojure.core$pos_int_QMARK_ 0x3840b4e6 "clojure.core$pos_int_QMARK_@3840b4e6"]}

# properties
* :properties/audiovisual  
  *  api.properties/create
* :properties/creature  
  *  api.properties/create
* :properties/item  
  *  api.properties/create
* :properties/projectile  
  *  api.properties/create
* :properties/skill  
  *  api.properties/create
* :properties/world  
  *  api.properties/create

# property
* :property/animation  {:widget :animation, :schema :some}
* :property/image  {:widget :image, :schema :some}
* :property/pretty-name  {:widget :text-field, :schema :string}
* :property/sound  {:widget :sound, :schema :string}

# screens
* :screens/game  
  *  api.screen/create
* :screens/main-menu  
  *  api.screen/create
* :screens/map-editor  
  *  api.screen/create
* :screens/minimap  
  *  api.screen/create
* :screens/options-menu  
  *  api.screen/create
* :screens/property-editor  
  *  api.screen/create

# skill
* :skill/action-time  {:widget :text-field, :schema #object[clojure.core$pos_QMARK_ 0x4155a965 "clojure.core$pos_QMARK_@4155a965"]}
* :skill/action-time-modifier-key  {:widget :enum, :schema [:enum :stats/cast-speed :stats/attack-speed], :items (:stats/cast-speed :stats/attack-speed)}
* :skill/cooldown  {:widget :text-field, :schema #object[clojure.core$nat_int_QMARK_ 0x5b7f245 "clojure.core$nat_int_QMARK_@5b7f245"]}
* :skill/cost  {:widget :text-field, :schema #object[clojure.core$nat_int_QMARK_ 0x5b7f245 "clojure.core$nat_int_QMARK_@5b7f245"]}
* :skill/effects  {:widget :nested-map, :schema [:map {:closed true} [:effect/kill # :boolean] [:effect/damage # #] [:effect/projectile # #] [:effect/stats-hp-set-to-max # #] [:effect/movement-speed # #] [:effect/hp # #] [:effect/stun # #object[clojure.core$pos_QMARK_ 0x4155a965 "clojure.core$pos_QMARK_@4155a965"]] [:effect/convert # :boolean] [:effect/spawn # #] [:effect/melee-damage # :some] [:effect/stats-mana-set-to-max # #] [:effect/target-entity # #] [:effect/mana # #]], :components (:effect/kill :effect/damage :effect/projectile :effect/stats-hp-set-to-max :effect/movement-speed :effect/hp :effect/stun :effect/convert :effect/spawn :effect/melee-damage :effect/stats-mana-set-to-max :effect/target-entity :effect/mana)}
* :skill/start-action-sound  {:widget :sound, :schema :string}

# stats
* :stats/armor-pierce  {:widget :text-field, :schema #object[clojure.core$number_QMARK_ 0x23bc053c "clojure.core$number_QMARK_@23bc053c"]}
* :stats/armor-save  {:widget :text-field, :schema #object[clojure.core$number_QMARK_ 0x23bc053c "clojure.core$number_QMARK_@23bc053c"]}
* :stats/attack-speed  {:widget :text-field, :schema #object[clojure.core$pos_QMARK_ 0x4155a965 "clojure.core$pos_QMARK_@4155a965"], :doc "action-time divided by this stat when a skill is being used.\n          Default value 1.\n\n          For example:\n          attack/cast-speed 1.5 => (/ action-time 1.5) => 150% attackspeed."}
* :stats/cast-speed  {:widget :text-field, :schema #object[clojure.core$pos_QMARK_ 0x4155a965 "clojure.core$pos_QMARK_@4155a965"], :doc "action-time divided by this stat when a skill is being used.\n          Default value 1.\n\n          For example:\n          attack/cast-speed 1.5 => (/ action-time 1.5) => 150% attackspeed."}
* :stats/hp  {:widget :text-field, :schema #object[clojure.core$pos_int_QMARK_ 0x3840b4e6 "clojure.core$pos_int_QMARK_@3840b4e6"]}
* :stats/mana  {:widget :text-field, :schema #object[clojure.core$nat_int_QMARK_ 0x5b7f245 "clojure.core$nat_int_QMARK_@5b7f245"]}
* :stats/modifiers  {:widget :nested-map, :schema [:map {:closed true} [:modifier/damage-deal # #] [:modifier/damage-receive # #]], :components [:modifier/damage-deal :modifier/damage-receive]}
* :stats/movement-speed  {:widget :text-field, :schema [:and #object[clojure.core$number_QMARK_ 0x23bc053c "clojure.core$number_QMARK_@23bc053c"] [:>= 0] [:<= 10.0]]}
* :stats/strength  {:widget :text-field, :schema #object[clojure.core$nat_int_QMARK_ 0x5b7f245 "clojure.core$nat_int_QMARK_@5b7f245"]}

# world
* :world/map-size  {:widget :text-field, :schema #object[clojure.core$pos_int_QMARK_ 0x3840b4e6 "clojure.core$pos_int_QMARK_@3840b4e6"]}
* :world/max-area-level  {:widget :text-field, :schema #object[clojure.core$pos_int_QMARK_ 0x3840b4e6 "clojure.core$pos_int_QMARK_@3840b4e6"]}
* :world/princess  {:schema [:qualified-keyword {:namespace :creatures}]}
* :world/spawn-rate  {:widget :text-field, :schema #object[clojure.core$pos_QMARK_ 0x4155a965 "clojure.core$pos_QMARK_@4155a965"]}
