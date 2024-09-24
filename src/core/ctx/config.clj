(ns ^:no-doc core.ctx.config
  (:require [core.component :as component]
            [core.ctx :refer :all]
            [core.ctx.property :as property]))

(property/def-attributes
  :tag [:enum [:dev :prod]]
  :configs :some)

(defcomponent :context/config
  {:data [:map [:tag :configs]]
   :let {:keys [tag configs]}}
  (component/create [_ _ctx]
    (get configs tag)))
