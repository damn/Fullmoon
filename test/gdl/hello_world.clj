#_(ns gdl.hello-world
  (:require [app.start :as app]
            [core.context :as ctx]
            [core.g :as g]))

#_(defrecord MyScreen []
  Screen
  (show [_ _context])
  (hide [_ _context])
  (render [_]
    (ctx/render-gui-view ctx #(g/draw-text % {:text "Hello World!" :x 400, :y 300}))))

#_(defn create-context [default-context]
  (assoc default-context :context/screens {:my-screen (->MyScreen)
                                           :first-screen :my-screen}))

#_(defn -main []
  (app/start {:app {:title "Hello World"
                    :width 800
                    :height 600
                    :full-screen? false}
              :create-context create-context}))
