(ns widgets.debug-window
  (:require [gdx.graphics :as graphics]
            [gdx.graphics.orthographic-camera :as orthographic-camera]
            [api.context :as ctx :refer [mouse-on-stage-actor? ->actor ->window ->label]]
            [api.graphics :as g]
            [api.scene2d.group :refer [add-actor!]]
            [api.scene2d.ui.label :refer [set-text!]]
            [api.scene2d.ui.widget-group :refer [pack!]]))

(defn- skill-info [{:keys [entity/skills]}]
  (clojure.string/join "\n"
                       (for [{:keys [property/id skill/cooling-down?]} (vals skills)
                             :when cooling-down? ]
                         [id [:cooling-down? (boolean cooling-down?)]])))

; TODO component to info-text move to the component itself.....
(defn- debug-infos [ctx]
  (let [world-mouse (ctx/world-mouse-position ctx)]
    (str
     "logic-frame: " (ctx/logic-frame ctx) "\n"
     "FPS: " (graphics/frames-per-second)  "\n"
     "Zoom: " (orthographic-camera/zoom (ctx/world-camera ctx)) "\n"
     "World: "(mapv int world-mouse) "\n"
     "X:" (world-mouse 0) "\n"
     "Y:" (world-mouse 1) "\n"
     "GUI: " (ctx/gui-mouse-position ctx) "\n"
     "paused? " (ctx/game-paused? ctx) "\n"
     "elapsed-time " (utils.core/readable-number (ctx/elapsed-time ctx)) " seconds \n"
     (skill-info (ctx/player-entity* ctx))
     (when-let [entity* (ctx/mouseover-entity* ctx)]
       (str "Mouseover-entity uid: " (:entity/uid entity*)))
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
    (add-actor! window (->actor context {:act #(do
                                                (set-text! label (debug-infos %))
                                                (pack! window))}))
    window))
