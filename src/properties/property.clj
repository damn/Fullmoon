(ns properties.property
  (:require [core.component :as component]
            [core.data :as data]))

(component/def :property/image       data/image)
(component/def :property/sound       data/sound)
(component/def :property/pretty-name data/string-attr)
