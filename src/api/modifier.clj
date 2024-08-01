(ns api.modifier
  (:refer-clojure :exclude [keys apply reverse])
  (:require [core.component :refer [defsystem]]))

(defsystem text [_])
(defsystem keys [_])
(defsystem apply   [_ value])
(defsystem reverse [_ value])
