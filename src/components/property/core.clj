(ns components.property.core
  (:require [core.component :as component :refer [defcomponent]]))

(com.badlogic.gdx.graphics.Colors/put
 "ITEM_GOLD"
 (com.badlogic.gdx.graphics.Color. (float 0.84)
                                   (float 0.8)
                                   (float 0.52)
                                   (float 1)))

(defcomponent :property/pretty-name
  {:data :string
   :let value}
  (component/info-text [_ _ctx]
    (str "[ITEM_GOLD]"value"[]")))
