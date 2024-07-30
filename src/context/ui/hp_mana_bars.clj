(ns context.ui.hp-mana-bars
  (:require [api.context :as ctx :refer [create-image get-sub-image ->actor]]
            [gdl.graphics :as g]
            [utils.core :refer [readable-number]]
            [data.val-max :refer [val-max-ratio]]))

(defn- render-infostr-on-bar [{:keys [gui-viewport-width] :as g} infostr y h]
  (g/draw-text g
               {:text infostr
                :x (/ gui-viewport-width 2)
                :y (+ y 2)
                :up? true}))

(defn ->hp-mana-bars [context]
  (let [rahmen (create-image context "ui/rahmen.png")
        rahmenw (first  (:pixel-dimensions rahmen))
        rahmenh (second (:pixel-dimensions rahmen))
        hpcontent   (create-image context "ui/hp.png")
        manacontent (create-image context "ui/mana.png")
        render-hpmana-bar (fn [{g :gdl.libgdx.context/graphics :as ctx} x y contentimg minmaxval name]
                            (g/draw-image g rahmen [x y])
                            (g/draw-image g
                                          (get-sub-image ctx (assoc contentimg :sub-image-bounds [0 0 (* rahmenw (val-max-ratio minmaxval)) rahmenh]))
                                          [x y])
                            (render-infostr-on-bar g (str (readable-number (minmaxval 0)) "/" (minmaxval 1) " " name) y rahmenh))]
    (->actor context
             {:draw (fn [{:keys [context/player-entity] :as ctx}]
                      (let [x (- (/ (ctx/gui-viewport-width ctx) 2)
                                 (/ rahmenw 2))
                            y-hp 5
                            y-mana (+ y-hp rahmenh)]
                        (render-hpmana-bar ctx x y-hp   hpcontent   (:entity/hp   @player-entity) "HP")
                        (render-hpmana-bar ctx x y-mana manacontent (:entity/mana @player-entity) "MP")))})))
