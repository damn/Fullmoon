(ns components.context.config
  (:require [core.component :refer [defcomponent] :as component]))

(defcomponent :tag {:data [:enum [:dev :prod]]})

(defcomponent :configs {:data :some})

(defcomponent :context/config
  {:data [:map [:tag :configs]]
   :let {:keys [tag configs]}}
  (component/create [_ _ctx]
    (get configs tag)))
