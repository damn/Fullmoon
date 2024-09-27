(ns core.app
  (:require [clojure.ctx :refer [start-app!]]
            core.creature
            core.projectile
            core.screens
            core.stat
            core.skill))

(.bindRoot #'clojure.ctx/info-text-k-order
           [:property/pretty-name
            :skill/action-time-modifier-key
            :skill/action-time
            :skill/cooldown
            :skill/cost
            :skill/effects
            :creature/species
            :creature/level
            :entity/stats
            :entity/delete-after-duration
            :projectile/piercing?
            :entity/projectile-collision
            :maxrange
            :entity-effects])

(.bindRoot #'clojure.ctx/property-k-sort-order
           [:property/id
            :property/pretty-name
            :app/lwjgl3
            :entity/image
            :entity/animation
            :creature/species
            :creature/level
            :entity/body
            :item/slot
            :projectile/speed
            :projectile/max-range
            :projectile/piercing?
            :skill/action-time-modifier-key
            :skill/action-time
            :skill/start-action-sound
            :skill/cost
            :skill/cooldown])

(defn -main []
  (start-app! "resources/properties.edn"))
