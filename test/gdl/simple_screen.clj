#_(ns gdl.simple-screen
  (:require [core.component :refer [defcomponent] :as component]
            [core.g :as g])
  (:import com.badlogic.gdx.graphics.Color))

#_(defn draw-test [g {{:keys [special-font logo]} :gdl/simple :as ctx}]
  (let [[wx wy] (map #(format "%.2f" %) (ctx/world-mouse-position g))
        [gx gy] (ctx/gui-mouse-position ctx)
        the-str (str "World x " wx "\n"
                     "World y " wy "\n"
                     "GUI x " gx "\n"
                     "GUI y " gy "\n")]
    (g/draw-centered-image g logo [gx (+ gy 230)])
    (g/draw-circle g [gx gy] 170 Color/WHITE)
    (g/draw-text g
                 {:text (str "default-font\n" the-str)
                  :x gx,:y gy,:h-align nil,:up? true})
    (g/draw-text g
                 {:font special-font
                  :text (str "exl-font\n" the-str)
                  :x gx,:y gy,:h-align :left,:up? false
                  :scale 2})))

#_(defrecord Screen []
  screen/Screen
  (show [_ _ctx])
  (hide [_ _ctx])
  (render [_]
    (ctx/render-gui-view ctx #(draw-test % ctx))))

#_(defcomponent :gdl/simple-screen
  (component/create [_ _ctx]
    (->Screen)))
