(ns property.all
  (:require [core.component :as component]
            [core.data :as data]))

(component/def :property/pretty-name data/string-attr)
(component/def :property/image       data/image)
(component/def :property/animation   data/animation)
(component/def :property/sound       data/sound)
