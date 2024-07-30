(ns context.error-modal
  (:require [api.context :refer [->window ->label add-to-stage!]]))

(extend-type api.context.Context
  api.context/ErrorModal
  (->error-window [ctx throwable]
    (add-to-stage! ctx
                   (->window ctx {:title "Error"
                                  :rows [[(->label ctx (str throwable))]]
                                  :modal? true
                                  :close-button? true
                                  :close-on-escape? true
                                  :center? true
                                  :pack? true}))))
