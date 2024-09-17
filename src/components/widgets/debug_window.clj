(ns components.widgets.debug-window
  (:require [gdx.graphics.camera :as camera]
            utils.core
            [core.context :as ctx :refer [mouse-on-stage-actor?]]
            [core.graphics :as g]
            [gdx.scene2d.group :refer [add-actor!]]
            [gdx.scene2d.ui :as ui]
            [gdx.scene2d.ui.label :refer [set-text!]]
            [gdx.scene2d.ui.widget-group :refer [pack!]])
  (:import com.badlogic.gdx.Gdx))

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
     "FPS: " (.getFramesPerSecond Gdx/graphics)  "\n"
     "Zoom: " (camera/zoom (ctx/world-camera ctx)) "\n"
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
              "id: " (gdx.scene2d.actor/id actor)
              )))))

(defn create [context]
  (let [label (ui/->label "")
        window (ui/->window {:title "Debug"
                             :id :debug-window
                             :visible? false
                             :position [0 (ctx/gui-viewport-height context)]
                             :rows [[label]]})]
    (add-actor! window (ui/->actor context {:act #(do
                                                   (set-text! label (debug-infos %))
                                                   (pack! window))}))
    window))
