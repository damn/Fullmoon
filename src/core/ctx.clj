; TODO:
; * clone vim clojure static
; * add a different color (clj-green, or anything unused, gold) for ctx functions
; ( * and 'ctx' can also have a special color?! )
(ns core.ctx
  (:require [core.ctx.assets :as assets]))

; TODO docstrings not avilable here ...
(def play-sound! assets/play-sound!)
(def texture     assets/texture)
