(ns core.ctx.screens
  (:require [core.component :refer [defcomponent] :as component]
            [core.screen :as screen]))

(defcomponent :context/screens
  {:data :some
   :let screen-ks}
  (component/create [_ ctx]
    {:screens (component/create-vs (zipmap screen-ks (repeat nil)) ctx)
     :first-screen (first screen-ks)})

  (component/destroy! [_]
    ; TODO screens not disposed https://github.com/damn/core/issues/41
    ))

(defn current-screen-key [{{:keys [current-screen]} :context/screens}]
  current-screen)

(defn current-screen [{{:keys [current-screen screens]} :context/screens}]
  [current-screen (get screens current-screen)])

(defn change-screen
  "Calls screen/exit on the current-screen (if there is one).
  Throws AssertionError when the context does not have a new-screen.
  Calls screen/enter on the new screen and
  returns the context with current-screen set to new-screen."
  [{{:keys [current-screen screens]} :context/screens :as context}
   new-screen-key]
  (when-let [screen-v (and current-screen
                           (current-screen screens))]
    (screen/exit [current-screen screen-v] context))

  (let [screen-v (new-screen-key screens)
        _ (assert screen-v (str "Cannot find screen with key: " new-screen-key))
        new-context (assoc-in context [:context/screens :current-screen] new-screen-key)]
    (screen/enter [new-screen-key screen-v] new-context)
    new-context))

(defn set-first-screen [context]
  (->> context
       :context/screens
       :first-screen
       (change-screen context)))

(defn render! [app-state]
  (screen/render! (current-screen @app-state) app-state))
