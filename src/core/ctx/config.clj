(ns ^:no-doc core.ctx.config
  (:require [core.ctx :refer :all]))

(def-attributes
  :tag [:enum [:dev :prod]]
  :configs :some)

(defcomponent :context/config
  {:data [:map [:tag :configs]]
   :let {:keys [tag configs]}}
  (->mk [_ _ctx]
    (get configs tag)))
