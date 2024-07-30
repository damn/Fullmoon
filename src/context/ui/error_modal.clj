(ns context.ui.error-modal
  (:require [gdl.context :refer [->window ->label add-to-stage!]]
            cdq.api.context))

(extend-type gdl.context.Context
  cdq.api.context/ErrorModal
  (->error-window [ctx throwable]
    (add-to-stage! ctx
                   (->window ctx {:title "Error"
                                  :rows [[(->label ctx (str throwable))]]
                                  :modal? true
                                  :close-button? true
                                  :close-on-escape? true
                                  :center? true
                                  :pack? true}))))
