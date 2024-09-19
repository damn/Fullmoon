(ns components.widgets.entity-info-window
  (:require [core.info :as info]
            [core.context :as ctx]
            [gdx.scene2d.ui :as ui]
            [gdx.scene2d.group :refer [add-actor!]]))

(def ^:private disallowed-keys [:entity/skills
                                :entity/state
                                :entity/faction
                                :active-skill])

(defn create [context]
  (let [label (ui/->label "")
        window (ui/->window {:title "Info"
                             :id :entity-info-window
                             :visible? false
                             :position [(ctx/gui-viewport-width context) 0]
                             :rows [[{:actor label :expand? true}]]})]
    ; TODO do not change window size ... -> no need to invalidate layout, set the whole stage up again
    ; => fix size somehow.
    (add-actor! window (ui/->actor context {:act (fn update-label-text [ctx]
                                                   ; items then have 2x pretty-name
                                                   #_(.setText (.getTitleLabel window)
                                                               (if-let [entity* (ctx/mouseover-entity* ctx)]
                                                                 (core.component/info-text [:property/pretty-name (:property/pretty-name entity*)])
                                                                 "Entity Info"))
                                                   (.setText label
                                                             (str (when-let [entity* (ctx/mouseover-entity* ctx)]
                                                                    (info/->text
                                                                     ; don't use select-keys as it loses core.entity.Entity record type
                                                                     (apply dissoc entity* disallowed-keys)
                                                                     ctx))))
                                                   (.pack window))}))
    window))
