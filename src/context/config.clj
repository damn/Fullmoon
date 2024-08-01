(ns context.config
  (:require [core.component :as component]))

(component/def :context/config {}
  {:keys [tag configs]}
  (component/create [_ _ctx]
    (get configs tag)))
