(ns gdl.context.screens
  (:require [core.component :as component]
            [gdl.context :as ctx]
            [gdl.screen :as screen]))

(component/def :gdl.context/screens {}
  _
  (ctx/create [[_ {:keys [screens] :as this}] ctx]
    (component/load! screens)
    (update this :screens component/update-map screen/create ctx))

  (ctx/destroy [_ ctx]
               ; TODO dispose all screen stages ....
               ; call dispose ?
               ; is it doing anything? because has batch right ? but stuff ... idk
               )

  (ctx/render [_ ctx]
    (screen/render (ctx/current-screen ctx) ctx)))

; TODO make some of these fns private ?
(extend-type gdl.context.Context
  gdl.context/ApplicationScreens
  (current-screen [{{:keys [current-screen screens]} :gdl.context/screens}]
    (get screens current-screen))

  (change-screen [{{:keys [current-screen screens]} :gdl.context/screens :as context}
                  new-screen-key]
    (when-let [screen (and current-screen
                           (current-screen screens))]
      (screen/hide screen context))
    (let [screen (new-screen-key screens)
          _ (assert screen (str "Cannot find screen with key: " new-screen-key))
          new-context (assoc-in context [:gdl.context/screens :current-screen] new-screen-key)]
      (screen/show screen new-context)
      new-context)))

(defn init-first-screen [context]
  (assert (:first-screen (:gdl.context/screens context)))
  (->> context
       :gdl.context/screens
       :first-screen
       (ctx/change-screen context)))
