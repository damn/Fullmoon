(ns core.components
  (:require [clojure.string :as str]
            [utils.core :refer [index-of]]
            [core.component :as component]))

(def ^:private k-order
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
   :entity/projectile-collision
   :maxrange
   :entity-effects])

(defn- sort-by-order [components]
  (sort-by (fn [[k _]] (or (index-of k k-order) 99))
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
               (str (component/info-text component
                                         (assoc ctx :info-text/entity* components))
                    (when (map? v)
                      (str "\n" (info-text v ctx))))))
       (str/join "\n")
       remove-newlines))
