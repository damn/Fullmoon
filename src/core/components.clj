(ns core.components
  (:require [clojure.string :as str]
            [core.component :as component]))

(def ^:private property-order [:property/id
                               :property/image
                               :property/pretty-name
                               :property/bounds])

(def ^:private item-order [:item/slot
                           :item/modifiers])

(def ^:private skill-order [:skill/action-time-modifier-key
                            :skill/action-time
                            :skill/cooldown
                            :skill/cost
                            :skill/effects
                            :skill/start-action-sound])

(def ^:private entity-order [:creature/species
                             :creature/level
                             :entity/animation
                             :entity/flying?
                             :entity/reaction-time
                             :entity/faction
                             :entity/state
                             :entity/stats
                             :entity/delete-after-duration
                             :entity/projectile-collision])

(def ^:private k-order
  (vec (concat property-order
               item-order
               skill-order
               entity-order)))

(defn- index-of [v k]
  (let [idx (.indexOf v k)]
    (if (= -1 idx)
      nil
      idx)))

(defn sort-by-order [components]
  (sort-by (fn [[k _]]
             (or (index-of k-order k) 99))
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
