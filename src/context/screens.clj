(ns context.screens
  (:require [core.component :refer [defcomponent] :as component]
            [core.context :as ctx]
            [core.screen :as screen]))

(defcomponent :context/screens {}
  screens
  (component/create [_ ctx]
    (let [first-screen (first screens)
          screens (zipmap screens (repeat true))]
      (component/load! screens)
      {:screens (component/update-map screens component/create ctx)
       :first-screen first-screen}))

  (component/destroy [_ ctx]
    ; TODO dispose all screen stages ....
    ; call dispose ?
    ; is it doing anything? because has batch right ? but stuff ... idk

    ; that means we automatically add a stage-screen to each screen??
    ; _ why not! _ ?

    ; does stage even need disposing? batch is passed as arg ..
    ; what is there to dispose ?
    ; i think I checked this before ...

    ))

; TODO make some of these fns private ?
(extend-type core.context.Context
  core.context/ApplicationScreens
  (current-screen-key [{{:keys [current-screen]} :context/screens}]
    current-screen)

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
      new-context))

  (init-first-screen [context]
    (assert (:first-screen (:context/screens context)))
    (->> context
         :context/screens
         :first-screen
         (ctx/change-screen context))))
