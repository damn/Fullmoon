# (:schema (k->data k) or component/data-component DRY


# Optimize _only_ for happyness
programmer happyness
whatever


maybe malli dependencies even put into
a core namespace - interface on it ?

# ingame rightclick window/context-menu on an entity
=> opens editor or other tools ....
(comment
 ; edit ingame
 ; cursor not changing becuz manual update
 (open-property-editor-window! @app/state (:property/id (ctx/mouseover-entity* @app/state)))
 )

# TODO comments in code // commented out // dead code
not findable
going crazy
no more comments -> only link w. tickets or delete.

like big TODO @ stats / actionbar / ?
src/components/entity/temp_modifier.clj
context/world passed vampire value,...
dead-code minimap/skill-window/replay-mode.

@ attribute-schema
 ; TODO here allow to pass :schema itself but then how 2 find it ...
 (make map schemas in 1 form )

# Make stuff greppable
 * #:component {:doc :schema :data :etc.}

# Use malli schema directly map
 * optional defined in map

# Components is just a map with optional stuff

# Reuse at property-editor :map schema for property itself

# Programmer Happyness #1 !

# b/src/components/context/properties.clj
apply-vs == component/apply-system

https://github.com/opqdonut/malli-edn-editor

# Attr-m = component definition itself

(comment
 (require '[malli.generator :as mg])
 (mg/generate (:schema (component/k->data :effect.entity/damage)))
 )

; damage optional ...

first have to fix optional ...


# TODO next remove :items/ and use :component/schema directly or :c/schema instead of data
; and skip all already defined (some/boolean/string) dont need to define here as defcomponents
; then allow ks to define directly schema see properties/app no need extra defcomponents with :data/:schema (grep :data/:schema)
