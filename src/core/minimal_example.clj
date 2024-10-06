(ns core.minimal-example
  (:require [clojure.gdx :refer :all]))

; 1. why are screens components? they are not components of anything
; just to pass to :context/screens with a keyword ?

; 2. no clear interface ....

; 3. :views key not necessary -> just gui-view/world-view

; 4. world-view optional?

; 5. screens itself optional?

(defcomponent :screens/foo
  (->mk [_] {})
  (screen-render! [_]))

(def app-config
  {:app/lwjgl3 {:title "Core",
                :width 1440
                :height 900,
                :fps 60,
                :full-screen? false}
   :app/context {:context/graphics {:views {:gui-view {:world-width 1440
                                                       :world-height 900},
                                            :world-view {:tile-size 48,
                                                         :world-width 1440
                                                         :world-height 900}}}
                 :context/screens [:screens/foo]}})

(defn -main []
  (start-app! app-config))
