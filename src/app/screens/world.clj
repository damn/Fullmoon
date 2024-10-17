(ns app.screens.world
  (:require [gdx.graphics :as g]
            [gdx.graphics.camera :as 🎥]
            [gdx.input :refer [key-pressed? key-just-pressed?]]
            [gdx.ui :as ui]
            [gdx.ui.actor :as a]
            [gdx.screen :as screen]
            [gdx.ui.stage :as stage]
            [gdx.ui.stage-screen :as stage-screen :refer [stage-get]]
            [level.generate :as level]
            [utils.core :refer [dev-mode?]]
            [moon.creature :as creature]
            moon.creature.player.item-on-cursor
            [moon.widgets.action-bar :as action-bar]
            [moon.widgets.debug-window :as debug-window]
            [moon.widgets.entity-info-window :as entity-info-window]
            [moon.widgets.hp-mana :as hp-mana-bars]
            [moon.widgets.inventory :as inventory]
            [moon.widgets.player-message :as player-message]
            moon.widgets.player-modal
            [world.core :as world]

            moon.audiovisual
            moon.projectile
            world.entity.animation
            world.entity.delete-after-duration
            world.entity.image
            world.entity.line
            world.entity.movement
            world.entity.string-effect
            world.effect.entity
            world.effect.target
            world.entity.stats))

(defn- hotkey->window-id []
  (merge {:keys/i :inventory-window
          :keys/e :entity-info-window}
         (when dev-mode?
           {:keys/z :debug-window})))

(defn- check-window-hotkeys []
  (doseq [[hotkey window-id] (hotkey->window-id)
          :when (key-just-pressed? hotkey)]
    (a/toggle-visible! (get (:windows (stage-get)) window-id))))

(defn- close-windows?! []
  (let [windows (ui/children (:windows (stage-get)))]
    (if (some a/visible? windows)
      (do
       (run! #(a/set-visible! % false) windows)
       true))))

(defn- adjust-zoom [camera by] ; DRY map editor
  (🎥/set-zoom! camera (max 0.1 (+ (🎥/zoom camera) by))))

(def ^:private zoom-speed 0.05)

(defn- check-zoom-keys []
  (let [camera (g/world-camera)]
    (when (key-pressed? :keys/minus)  (adjust-zoom camera    zoom-speed))
    (when (key-pressed? :keys/equals) (adjust-zoom camera (- zoom-speed)))))

; TODO move to actor/stage listeners ? then input processor used ....
(defn- check-key-input []
  (check-zoom-keys)
  (check-window-hotkeys)
  (cond (and (key-just-pressed? :keys/escape)
             (not (close-windows?!)))
        (screen/change! :screens/options-menu)

        ; TODO not implementing StageSubScreen so NPE no screen-render!
        #_(key-just-pressed? :keys/tab)
        #_(screen/change! :screens/minimap)))

(deftype WorldScreen []
  screen/Screen
  (screen/enter! [_])

  (screen/exit! [_]
    (g/set-cursor! :cursors/default))

  (screen/render! [_]
    (world/tick!)
    (check-key-input))

  (screen/dispose! [_]))

(defn create []
  [:screens/world (stage-screen/create :screen (->WorldScreen))])

(defn- world-actors []
  [(ui/table {:rows [[{:actor (action-bar/create)
                       :expand? true
                       :bottom? true}]]
              :id :action-bar-table
              :cell-defaults {:pad 2}
              :fill-parent? true})
   (hp-mana-bars/create)
   (ui/group {:id :windows
              :actors [(debug-window/create)
                       (entity-info-window/create)
                       (inventory/create)]})
   (ui/actor {:draw moon.creature.player.item-on-cursor/draw-item-on-cursor})
   (player-message/create)])

(defn- reset-stage! []
  (let [stage (stage-get)] ; these fns to stage itself
    (stage/clear! stage)
    (run! #(stage/add! stage %) (world-actors))))

(defn start-game-fn [world-id]
  (fn []
    (screen/change! :screens/world)
    (reset-stage!)
    (let [level (level/generate-level world-id)]
      (world/init! (:tiled-map level))
      (creature/spawn-all level))))
