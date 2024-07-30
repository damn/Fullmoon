(ns context.background-image
  (:require [core.component :as component]
            [gdl.context :as ctx]
            api.context))

(component/def :context/background-image {}
  file
  (ctx/create [_ ctx] (ctx/create-image ctx file)))

(extend-type gdl.context.Context
  api.context/BackgroundImage
  (->background-image [ctx]
    (ctx/->image-widget ctx
                        (:context/background-image ctx)
                        {:fill-parent? true
                         :scaling :fill
                         :align :center})))
