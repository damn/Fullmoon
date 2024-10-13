(ns world.entity.faction
  (:require [core.component :refer [defc] :as component]))

(defn enemy [{:keys [entity/faction]}]
  (case faction
    :evil :good
    :good :evil))

(defn friend [{:keys [entity/faction]}]
  faction)

(defc :entity/faction
  {:data [:enum :good :evil]
   :let faction}
  (component/info [_]
    (str "[SLATE]Faction: " (name faction) "[]")))

