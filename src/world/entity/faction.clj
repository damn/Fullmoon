(ns world.entity.faction
  (:require [core.component :refer [defc]]
            [core.info :as info]))

(defn enemy [{:keys [entity/faction]}]
  (case faction
    :evil :good
    :good :evil))

(defn friend [{:keys [entity/faction]}]
  faction)

(defc :entity/faction
  {:db/schema [:enum :good :evil]
   :let faction}
  (info/text [_]
    (str "[SLATE]Faction: " (name faction) "[]")))

