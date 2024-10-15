(ns core.widgets.hp-mana
  (:require [clojure.gdx.graphics :as g]
            [clojure.gdx.ui :as ui]
            [core.val-max :as val-max]
            [utils.core :refer [readable-number]]
            [world.entity.stats :refer [entity-stat]]
            [world.player :refer [world-player]]))

(defn- render-infostr-on-bar [infostr x y h]
  (g/draw-text {:text infostr
                :x (+ x 75)
                :y (+ y 2)
                :up? true}))

(defn create []
  (let [rahmen      (g/image "images/rahmen.png")
        hpcontent   (g/image "images/hp.png")
        manacontent (g/image "images/mana.png")
        x (/ (g/gui-viewport-width) 2)
        [rahmenw rahmenh] (:pixel-dimensions rahmen)
        y-mana 80 ; action-bar-icon-size
        y-hp (+ y-mana rahmenh)
        render-hpmana-bar (fn [x y contentimg minmaxval name]
                            (g/draw-image rahmen [x y])
                            (g/draw-image (g/sub-image contentimg [0 0 (* rahmenw (val-max/ratio minmaxval)) rahmenh])
                                          [x y])
                            (render-infostr-on-bar (str (readable-number (minmaxval 0)) "/" (minmaxval 1) " " name) x y rahmenh))]
    (ui/actor {:draw (fn []
                       (let [player-entity @world-player
                             x (- x (/ rahmenw 2))]
                         (render-hpmana-bar x y-hp   hpcontent   (entity-stat player-entity :stats/hp) "HP")
                         (render-hpmana-bar x y-mana manacontent (entity-stat player-entity :stats/mana) "MP")))})))
