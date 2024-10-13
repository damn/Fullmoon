(ns clojure.gdx.app-test
  (:require [clojure.gdx.app :as app]))

(defn -main []
  (app/start! (reify app/Listener
                (create! [_])
                (dispose! [_])
                (render! [_])
                (resize! [_ dimensions]))
              {:title "Hello window"
               :width 800
               :height 600
               :full-screen? false}))
