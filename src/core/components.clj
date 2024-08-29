(ns core.components
  (:require [clojure.string :as str]
            [core.component :as component]))

; order should be defined for all entity-types
; e.g. target-all or target-entity range comes after/before entity-effects

(def ^:private properties-order [:properties/app
                                 :properties/skill
                                 :properties/creature
                                 :properties/item
                                 :properties/world
                                 :properties/audiovisual
                                 :properties/projectile])

(def ^:private property-order [:property/id
                               :entity/image
                               :property/pretty-name])

(def ^:private item-order [:item/slot
                           :item/modifiers])

(def ^:private skill-order [:skill/action-time-modifier-key
                            :skill/action-time
                            :skill/cooldown
                            :skill/cost
                            :skill/effects
                            :skill/start-action-sound])

(def ^:private entity-order [:entity/body
                             :creature/species
                             :creature/level
                             :entity/animation
                             :entity/faction
                             :entity/state
                             :entity/stats
                             :entity/delete-after-duration
                             :entity/projectile-collision])

(def ^:private effect-order [:maxrange
                             :entity-effects])

(def ^:private k-order
  (vec (concat properties-order
               property-order
               item-order
               skill-order
               entity-order
               effect-order)))

(defn sort-index-of [k]
  (let [idx (.indexOf k-order k)]
    (if (= -1 idx)
      99
      idx)))

(defn sort-by-order [components]
  (sort-by (fn [[k _]] (sort-index-of k))
           components))

(defn- remove-newlines [s]
  (let [new-s (-> s
                  (str/replace "\n\n" "\n")
                  (str/replace #"^\n" "")
                  str/trim-newline)]
    (if (= (count new-s) (count s))
      s
      (remove-newlines new-s))))

(defn info-text [components ctx]
  (->> components
       sort-by-order
       (keep (fn [{v 1 :as component}]
               (str (component/info-text component ctx)
                    (when (map? v)
                      (str "\n" (info-text v ctx))))))
       (str/join "\n")
       remove-newlines))
