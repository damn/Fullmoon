(ns context.background-image
  (:require [core.component :as component]
            [api.context :as ctx]))

(component/def :context/background-image {}
  file
  (ctx/create [_ ctx] (ctx/create-image ctx file)))

(extend-type api.context.Context
  api.context/BackgroundImage
  (->background-image [ctx]
    (ctx/->image-widget ctx
                        (:context/background-image ctx)
                        {:fill-parent? true
                         :scaling :fill
                         :align :center})))
