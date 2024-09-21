(ns core.context.screens
  (:require [core.component :refer [defcomponent] :as component]
            [core.context :as ctx]
            [core.state :as state]))

(defcomponent :context/screens
  {:data :some
   :let screen-ks}
  (component/create [_ ctx]
    {:screens (component/create-vs (zipmap screen-ks (repeat nil)) ctx)
     :first-screen (first screen-ks)})

  (component/destroy! [_]
    ; TODO screens not disposed https://github.com/damn/core/issues/41
    ))

(extend-type core.context.Context
  core.context/ApplicationScreens
  (current-screen-key [{{:keys [current-screen]} :context/screens}]
    current-screen)

  (current-screen [{{:keys [current-screen screens]} :context/screens}]
    [current-screen (get screens current-screen)])

  (change-screen [{{:keys [current-screen screens]} :context/screens :as context}
                  new-screen-key]
    (when-let [screen-v (and current-screen
                             (current-screen screens))]
      (state/exit [current-screen screen-v] context))

    (let [screen-v (new-screen-key screens)
          _ (assert screen-v (str "Cannot find screen with key: " new-screen-key))
          new-context (assoc-in context [:context/screens :current-screen] new-screen-key)]
      (state/enter [new-screen-key screen-v] new-context)
      new-context))

  (init-first-screen [context]
    (->> context
         :context/screens
         :first-screen
         (ctx/change-screen context))))
