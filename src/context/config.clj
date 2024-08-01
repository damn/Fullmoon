(ns context.config
  (:require [core.component :refer [defcomponent] :as component]))

(defcomponent :context/config {}
  (component/create [[_ {:keys [tag configs]}] _ctx]
    (get configs tag)))
