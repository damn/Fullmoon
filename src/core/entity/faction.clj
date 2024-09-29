(in-ns 'core.entity)

(defn enemy-faction [{:keys [entity/faction]}]
  (case faction
    :evil :good
    :good :evil))

(defn friendly-faction [{:keys [entity/faction]}]
  faction)

(defcomponent :entity/faction
  {:let faction
   :data [:enum [:good :evil]]}
  (info-text [_ _ctx]
    (str "[SLATE]Faction: " (name faction) "[]")))
