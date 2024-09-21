(ns core.widgets.background-image
  (:require [gdx.scene2d.ui :as ui]
            [core.context :as ctx]))

(def ^:private image-file "images/moon_background.png")

(extend-type core.context.Context
  core.context/BackgroundImage
  (->background-image [ctx]
    (ui/->image-widget (ctx/create-image ctx image-file)
                       {:fill-parent? true
                        :scaling :fill
                        :align :center})))
