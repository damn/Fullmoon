(ns gdl.simple
  (:require [core.component :as component]
            [gdl.libgdx.app :as app]
            [api.context :as ctx]))

(component/def :gdl/simple {}
  {:keys [font logo]}
  (ctx/create [_ ctx]
    {:special-font (ctx/generate-ttf ctx font)
     :logo (ctx/create-image ctx logo)}))

(defn app []
  (app/start {:app {:title "gdl demo"
                    :width 800
                    :height 600
                    :full-screen? false}
              :context [[:gdl.libgdx.context/graphics true]
                        [:gdl.libgdx.context/assets true]
                        [:gdl.libgdx.context/image-drawer-creator true]
                        [:gdl.libgdx.context/ui true]
                        [:context/screens {:first-screen :gdl/simple-screen
                                           :screens {:gdl/simple-screen true}}]
                        [:gdl/simple {:logo "logo.png"
                                      :font {:file "exocet/films.EXL_____.ttf"
                                             :size 16}}]]}))
