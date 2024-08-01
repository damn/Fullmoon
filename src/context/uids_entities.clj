(ns context.uids-entities
  (:require [core.component :as component]))

(component/def :context/uids-entities {}
  _
  (component/create [_ _ctx] (atom {})))
