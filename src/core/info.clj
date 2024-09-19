(ns core.info
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
   :projectile/piercing?
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

(defn ->text
  "Recursively generates info-text via [[core.component/info-text]]."
  [components ctx]
  (->> components
       sort-by-order
       (keep (fn [{v 1 :as component}]
               (str (try (component/info-text component (assoc ctx :info-text/entity* components))
                         (catch Throwable t
                           ; calling from property-editor where entity components
                           ; have a different data schema than after component/create
                           ; and info-text might break
                           (pr-str component)))
                    (when (map? v)
                      (str "\n" (->text v ctx))))))
       (str/join "\n")
       remove-newlines))
