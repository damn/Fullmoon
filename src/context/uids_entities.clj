(ns context.uids-entities
  (:require [core.component :refer [defcomponent] :as component]))

(defcomponent :context/uids-entities {}
  (component/create [_ _ctx]
    (atom {})))
