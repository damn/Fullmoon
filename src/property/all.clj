(ns property.all
  (:require [core.component :refer [defcomponent]]
            [core.data :as data]))

(defcomponent :property/pretty-name data/string-attr)
(defcomponent :property/image       data/image)
(defcomponent :property/animation   data/animation)
(defcomponent :property/sound       data/sound)
