(ns core.context.config
  (:require [core.component :refer [defcomponent] :as component]
            [core.data :as data]))

(data/def-attributes
  :tag [:enum [:dev :prod]]
  :configs :some)

(defcomponent :context/config
  {:data [:map [:tag :configs]]
   :let {:keys [tag configs]}}
  (component/create [_ _ctx]
    (get configs tag)))
