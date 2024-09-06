(ns components.tx.sound
  (:require [core.component :refer [defcomponent] :as component]
            [core.context :as ctx]))

(defcomponent :tx/sound
  {:data :sound
   :let file}
  (component/do! [_ ctx]
    (ctx/play-sound! ctx file)
    ctx))
