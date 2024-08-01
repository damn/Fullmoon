(ns context.background-image
  (:require [core.component :refer [defcomponent] :as component]
            [api.context :as ctx]))

(defcomponent :context/background-image {}
  (component/create [[_ file] ctx]
    (ctx/create-image ctx file)))

(extend-type api.context.Context
  api.context/BackgroundImage
  (->background-image [ctx]
    (ctx/->image-widget ctx
                        (:context/background-image ctx)
                        {:fill-parent? true
                         :scaling :fill
                         :align :center})))
