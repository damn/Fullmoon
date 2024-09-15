(ns core.modifiers
  (:require [clojure.string :as str]
            [utils.core :refer [k->pretty-name]]
            [core.operation :as operation])
  (:import com.badlogic.gdx.graphics.Color))

(com.badlogic.gdx.graphics.Colors/put "MODIFIER_BLUE"
                                      Color/CYAN
                                      ; maybe can be used in tooltip background is darker (from D2 copied color)
                                      #_(com.badlogic.gdx.graphics.Color. (float 0.48)
                                                                          (float 0.57)
                                                                          (float 1)
                                                                          (float 1)))

; For now no green/red color for positive/negative numbers
; as :stats/damage-receive negative value would be red but actually a useful buff
; -> could give damage reduce 10% like in diablo 2
; and then make it negative .... @ applicator
(def ^:private positive-modifier-color "[MODIFIER_BLUE]" #_"[LIME]")
(def ^:private negative-modifier-color "[MODIFIER_BLUE]" #_"[SCARLET]")

(defn info-text [modifiers]
  (str "[MODIFIER_BLUE]"
       (str/join "\n"
                 (for [[modifier-k operations] modifiers
                       operation operations]
                   (str (operation/info-text operation) " " (k->pretty-name modifier-k))))
       "[]"))
