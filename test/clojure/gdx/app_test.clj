(ns clojure.gdx.app-test
  (:require [clojure.gdx.app :as app]))

(defn -main []
  (app/start! (proxy [com.badlogic.gdx.ApplicationAdapter] []
                (create [])
                (dispose [])
                (render [])
                (resize [w h]))
              {:title "Hello window"
               :width 800
               :height 600
               :full-screen? false}))
