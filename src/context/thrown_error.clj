(ns context.thrown-error
  (:require [core.component :refer [defcomponent] :as component]
            [api.context :as ctx]))

(defcomponent :context/thrown-error {}
  (component/create [_ _ctx]
    (atom nil)))
