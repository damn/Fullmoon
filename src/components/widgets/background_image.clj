(ns components.widgets.background-image
  (:require [core.component :refer [defcomponent] :as component]
            [core.context :as ctx]))

(defcomponent :widgets/background-image {}
  (component/create [[_ file] _ctx]
    (def ^:private image-file file)
    nil))

(extend-type core.context.Context
  core.context/BackgroundImage
  (->background-image [ctx]
    (ctx/->image-widget ctx
                        (ctx/create-image ctx image-file)
                        {:fill-parent? true
                         :scaling :fill
                         :align :center})))
