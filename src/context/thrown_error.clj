(ns context.thrown-error
  (:require [core.component :as component]
            [api.context :as ctx]))

(component/def :context/thrown-error {}
  _
  (ctx/create [_ _ctx] (atom nil)))
