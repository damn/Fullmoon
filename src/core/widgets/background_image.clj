(ns ^:no-doc core.widgets.background-image
  (:require [core.ctx.ui :as ui]
            [core.graphics.image :as image]))

(def ^:private image-file "images/moon_background.png")

(defn ->background-image [ctx]
  (ui/->image-widget (image/create ctx image-file)
                     {:fill-parent? true
                      :scaling :fill
                      :align :center}))
