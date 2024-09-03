
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
