(ns context.game-logic-frame
  (:require [core.component :refer [defcomponent] :as component]))

(defcomponent :context/game-logic-frame {}
  (component/create [_ _ctx] (atom 0)))
