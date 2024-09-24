(ns ^:no-doc core.widgets.entity-info-window
  (:require [core.ctx :refer :all]
            [core.ui :as ui]
            [core.group :refer [add-actor!]]))

(def ^:private disallowed-keys [:entity/skills
                                :entity/state
                                :entity/faction
                                :active-skill])

(defn create [context]
  (let [label (ui/->label "")
        window (ui/->window {:title "Info"
                             :id :entity-info-window
                             :visible? false
                             :position [(gui-viewport-width context) 0]
                             :rows [[{:actor label :expand? true}]]})]
    ; TODO do not change window size ... -> no need to invalidate layout, set the whole stage up again
    ; => fix size somehow.
    (add-actor! window (ui/->actor {:act (fn update-label-text [ctx]
                                           ; items then have 2x pretty-name
                                           #_(.setText (.getTitleLabel window)
                                                       (if-let [entity* (mouseover-entity* ctx)]
                                                         (info-text [:property/pretty-name (:property/pretty-name entity*)])
                                                         "Entity Info"))
                                           (.setText label
                                                     (str (when-let [entity* (mouseover-entity* ctx)]
                                                            (->info-text
                                                             ; don't use select-keys as it loses core.entity.Entity record type
                                                             (apply dissoc entity* disallowed-keys)
                                                             ctx))))
                                           (.pack window))}))
    window))
