(ns widgets.error-modal
  (:require [api.context :as ctx]))

(defn error-window! [ctx throwable]
  (ctx/add-to-stage!
   ctx
   (ctx/->window ctx {:title "Error"
                      :rows [[(ctx/->label ctx (str throwable))]]
                      :modal? true
                      :close-button? true
                      :close-on-escape? true
                      :center? true
                      :pack? true})))
