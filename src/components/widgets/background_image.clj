(ns components.widgets.background-image
  (:require [core.context :as ctx]))

(def ^:private image-file "images/moon_background.png")

(extend-type core.context.Context
  core.context/BackgroundImage
  (->background-image [ctx]
    (ctx/->image-widget ctx
                        (ctx/create-image ctx image-file)
                        {:fill-parent? true
                         :scaling :fill
                         :align :center})))
