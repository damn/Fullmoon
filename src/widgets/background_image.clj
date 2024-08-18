(ns widgets.background-image
  (:require [api.context :as ctx]))

(defn ->background-image [ctx]
  (ctx/->image-widget ctx
                      (ctx/create-image ctx "images/moon_background.png")
                      {:fill-parent? true
                       :scaling :fill
                       :align :center}))
