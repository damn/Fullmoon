(ns context.uids-entities
  (:require [core.component :as component]
            [api.context :as ctx]))

(component/def :context/uids-entities {}
  _
  (ctx/create [_ _ctx] (atom {})))
