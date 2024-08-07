(ns widgets.debug-window
  (:require [api.context :as ctx :refer [frames-per-second mouse-on-stage-actor? ->actor ->window ->label]]
            [api.graphics :as g]
            [api.graphics.camera :as camera]
            [api.scene2d.group :refer [add-actor!]]
            [api.scene2d.ui.label :refer [set-text!]]
            [api.scene2d.ui.widget-group :refer [pack!]]))

(defn- skill-info [{:keys [entity/skills]}]
  (clojure.string/join "\n"
                       (for [{:keys [property/id skill/cooling-down?]} (vals skills)
                             :when cooling-down? ]
                         [id [:cooling-down? (boolean cooling-down?)]])))

(defn- debug-infos [{:keys [context/game-paused
                            context/player-entity
                            context/elapsed-game-time
                            context/game-logic-frame]
                     :as ctx}]
  (let [world-mouse (ctx/world-mouse-position ctx)]
    (str
     "game-logic-frame: " @game-logic-frame "\n"
     "FPS: " (frames-per-second ctx)  "\n"
     "Zoom: " (camera/zoom (ctx/world-camera ctx)) "\n"
     "World: "(mapv int world-mouse) "\n"
     "X:" (world-mouse 0) "\n"
     "Y:" (world-mouse 1) "\n"
     "GUI: " (ctx/gui-mouse-position ctx) "\n"
     (when @(ctx/entity-error ctx)
       (str "\nERROR!\n " @(ctx/entity-error ctx) "\n\n"))
     "game-paused " @game-paused "\n"
     "elapsed-game-time " (utils.core/readable-number @elapsed-game-time) " seconds \n"
     (skill-info @player-entity)
     ;"\nMouseover-Actor:\n"
     #_(when-let [actor (mouse-on-stage-actor? ctx)]
         (str "TRUE - name:" (.getName actor)
              "id: " (api.scene2d.actor/id actor)
              )))))

(defn create [context]
  (let [label (->label context "")
        window (->window context {:title "Debug"
                                  :id :debug-window
                                  :visible? false
                                  :position [0 (ctx/gui-viewport-height context)]
                                  :rows [[label]]})]
    (add-actor! window (->actor context
                                {:act
                                 #(do
                                   (set-text! label (debug-infos %))
                                   (pack! window))}))
    window))
