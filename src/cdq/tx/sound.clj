(ns cdq.tx.sound
  (:require [core.component :as component]
            [gdl.context :refer [play-sound!]]
            [cdq.api.context :refer [transact!]]
            [cdq.attributes :as attr]))

(component/def :tx/sound attr/sound
  file
  (transact! [_ ctx]
    (play-sound! ctx file)
    nil))
