(ns widgets.hp-mana-bars
  (:require [utils.core :as utils]
            [api.context :as ctx]
            [api.graphics :as g]
            [data.val-max :refer [val-max-ratio]]))

(defn- render-infostr-on-bar [g infostr x y h]
  (g/draw-text g {:text infostr
                  :x (+ x 75)
                  :y (+ y 2)
                  :up? true}))

(defn ->hp-mana-bars [context]
  (let [rahmen      (ctx/create-image context "ui/rahmen.png")
        hpcontent   (ctx/create-image context "ui/hp.png")
        manacontent (ctx/create-image context "ui/mana.png")
        x (/ (ctx/gui-viewport-width context) 2)
        [rahmenw rahmenh] (:pixel-dimensions rahmen)
        render-hpmana-bar (fn [g ctx x y contentimg minmaxval name]
                            (g/draw-image g rahmen [x y])
                            (g/draw-image g
                                          (ctx/get-sub-image ctx contentimg [0 0 (* rahmenw (val-max-ratio minmaxval)) rahmenh])
                                          [x y])
                            (render-infostr-on-bar g (str (utils/readable-number (minmaxval 0)) "/" (minmaxval 1) " " name) x y rahmenh))]
    (ctx/->actor context
                 {:draw (fn [g {:keys [context/player-entity] :as ctx}]
                          (let [x (- x (/ rahmenw 2))
                                y-hp 5
                                y-mana (+ y-hp rahmenh)]
                            (render-hpmana-bar g ctx x y-hp   hpcontent   (:entity/hp   @player-entity) "HP")
                            (render-hpmana-bar g ctx x y-mana manacontent (:entity/mana @player-entity) "MP")))})))
