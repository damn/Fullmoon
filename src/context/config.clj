(ns context.config
  (:require [core.component :as component]
            [api.context :as ctx]))

(component/def :context/config {}
  {:keys [tag configs]}
  (ctx/create [_ _ctx]
    (get configs tag)))
