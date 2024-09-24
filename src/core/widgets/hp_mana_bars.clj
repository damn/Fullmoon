(ns ^:no-doc core.widgets.hp-mana-bars
  (:require [core.utils.core :as utils]
            [core.val-max :refer [val-max-ratio]]
            [core.entity :as entity]
            [core.entity.player :as player]
            [core.graphics :as g]
            [core.graphics.image :as image]
            [core.graphics.views :refer [gui-viewport-width]]
            [core.ctx.ui :as ui]))

(defn- render-infostr-on-bar [g infostr x y h]
  (g/draw-text g {:text infostr
                  :x (+ x 75)
                  :y (+ y 2)
                  :up? true}))

(defn ->hp-mana-bars [context]
  (let [rahmen      (image/create context "images/rahmen.png")
        hpcontent   (image/create context "images/hp.png")
        manacontent (image/create context "images/mana.png")
        x (/ (gui-viewport-width context) 2)
        [rahmenw rahmenh] (:pixel-dimensions rahmen)
        y-mana 80 ; action-bar-icon-size
        y-hp (+ y-mana rahmenh)
        render-hpmana-bar (fn [g ctx x y contentimg minmaxval name]
                            (g/draw-image g rahmen [x y])
                            (g/draw-image g
                                          (image/sub-image ctx contentimg [0 0 (* rahmenw (val-max-ratio minmaxval)) rahmenh])
                                          [x y])
                            (render-infostr-on-bar g (str (utils/readable-number (minmaxval 0)) "/" (minmaxval 1) " " name) x y rahmenh))]
    (ui/->actor {:draw (fn [g ctx]
                         (let [player-entity* (player/entity* ctx)
                               x (- x (/ rahmenw 2))]
                           (render-hpmana-bar g ctx x y-hp   hpcontent   (entity/stat player-entity* :stats/hp) "HP")
                           (render-hpmana-bar g ctx x y-mana manacontent (entity/stat player-entity* :stats/mana) "MP")))})))
