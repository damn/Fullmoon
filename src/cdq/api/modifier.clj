(ns cdq.api.modifier
  (:refer-clojure :exclude [keys apply reverse])
  (:require [core.component :as component]))

(component/defn text [_])
(component/defn keys [_])
(component/defn apply   [_ value])
(component/defn reverse [_ value])
