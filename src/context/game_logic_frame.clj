(ns context.game-logic-frame
  (:require [core.component :as component]))

(component/def :context/game-logic-frame {}
  _
  (component/create [_ _ctx] (atom 0)))
