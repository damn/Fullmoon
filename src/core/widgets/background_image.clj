(ns core.widgets.background-image
  (:require [gdx.scene2d.ui :as ui]
            [core.graphics.image :as image]
            [core.context :as ctx]))

(def ^:private image-file "images/moon_background.png")

(extend-type core.context.Context
  core.context/BackgroundImage
  (->background-image [ctx]
    (ui/->image-widget (image/create ctx image-file)
                       {:fill-parent? true
                        :scaling :fill
                        :align :center})))
