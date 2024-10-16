(ns core.widgets.debug-window
  (:require [clojure.gdx.graphics :as g]
            [clojure.gdx.graphics.camera :as 🎥]
            [clojure.gdx.ui :as ui]
            [clojure.gdx.ui.stage-screen :refer [mouse-on-actor?]]
            [clojure.string :as str]
            [utils.core :refer [readable-number]]
            [world.core :as world]
            [world.mouseover-entity :refer [mouseover-entity]]))

(defn- skill-info [{:keys [entity/skills]}]
  (str/join "\n"
            (for [{:keys [property/id skill/cooling-down?]} (vals skills)
                  :when cooling-down? ]
              [id [:cooling-down? (boolean cooling-down?)]])))

(defn- debug-infos ^String []
  (let [world-mouse (g/world-mouse-position)]
    (str
     "logic-frame: " world/logic-frame "\n"
     "FPS: " (g/frames-per-second)  "\n"
     "Zoom: " (🎥/zoom (g/world-camera)) "\n"
     "World: "(mapv int world-mouse) "\n"
     "X:" (world-mouse 0) "\n"
     "Y:" (world-mouse 1) "\n"
     "GUI: " (g/gui-mouse-position) "\n"
     "paused? " world/paused? "\n"
     "elapsed-time " (readable-number world/elapsed-time) " seconds \n"
     "skill cooldowns: " (skill-info @world/player) "\n"
     (when-let [entity (mouseover-entity)]
       (str "Mouseover-entity id: " (:entity/id entity)))
     ;"\nMouseover-Actor:\n"
     #_(when-let [actor (mouse-on-actor?)]
         (str "TRUE - name:" (.getName actor)
              "id: " (a/id actor))))))

(defn create []
  (let [label (ui/label "")
        window (ui/window {:title "Debug"
                           :id :debug-window
                           :visible? false
                           :position [0 (g/gui-viewport-height)]
                           :rows [[label]]})]
    (ui/add-actor! window (ui/actor {:act #(do
                                            (.setText label (debug-infos))
                                            (.pack window))}))
    window))
