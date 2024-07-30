(ns gdl.simple-screen
  (:require [core.component :as component]
            [api.context :as ctx]
            [api.graphics :as g]
            [api.screen :as screen]
            [api.graphics.color :as color]))

(defn draw-test [g {{:keys [special-font logo]} :gdl/simple}]
  (let [[wx wy] (map #(format "%.2f" %) (g/world-mouse-position g))
        [gx gy] (g/gui-mouse-position g)
        the-str (str "World x " wx "\n"
                     "World y " wy "\n"
                     "GUI x " gx "\n"
                     "GUI y " gy "\n")]
    (g/draw-centered-image g logo [gx (+ gy 230)])
    (g/draw-circle g [gx gy] 170 color/white)
    (g/draw-text g
                 {:text (str "default-font\n" the-str)
                  :x gx,:y gy,:h-align nil,:up? true})
    (g/draw-text g
                 {:font special-font
                  :text (str "exl-font\n" the-str)
                  :x gx,:y gy,:h-align :left,:up? false
                  :scale 2})))

(defrecord Screen []
  screen/Screen
  (show [_ _ctx])
  (hide [_ _ctx])
  (render [_ {g :context.libgdx/graphics :as ctx}]
    (g/render-gui-view g #(draw-test % ctx))))

(component/def :gdl/simple-screen {}
  _
  (screen/create [_ _ctx] (->Screen)))
