(ns context.screens
  (:require [core.component :as component]
            [api.context :as ctx]
            [api.screen :as screen]))

(component/def :context/screens {}
  _
  (component/create [[_ {:keys [screens] :as this}] ctx]
    (component/load! screens)
    (update this :screens component/update-map screen/create ctx))

  (component/destroy [_ ctx]
               ; TODO dispose all screen stages ....
               ; call dispose ?
               ; is it doing anything? because has batch right ? but stuff ... idk
               )

  (ctx/render [_ ctx]
    (screen/render (ctx/current-screen ctx) ctx)))

; TODO make some of these fns private ?
(extend-type api.context.Context
  api.context/ApplicationScreens
  (current-screen [{{:keys [current-screen screens]} :context/screens}]
    (get screens current-screen))

  (change-screen [{{:keys [current-screen screens]} :context/screens :as context}
                  new-screen-key]
    (when-let [screen (and current-screen
                           (current-screen screens))]
      (screen/hide screen context))
    (let [screen (new-screen-key screens)
          _ (assert screen (str "Cannot find screen with key: " new-screen-key))
          new-context (assoc-in context [:context/screens :current-screen] new-screen-key)]
      (screen/show screen new-context)
      new-context)))

(defn init-first-screen [context]
  (assert (:first-screen (:context/screens context)))
  (->> context
       :context/screens
       :first-screen
       (ctx/change-screen context)))
