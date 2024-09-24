(ns ^:no-doc core.screens.world
  (:require [core.graphics.camera :as camera]
            [core.ctx :refer :all]
            [core.ctx.ecs :as ecs]
            [core.entity :as entity]
            [core.entity.player :as player]
            [core.graphics.cursors :as cursors]
            [core.graphics.views :refer [world-camera]]
            [core.ctx.screens :as screens]
            [core.ctx.widgets :as widgets]
            [core.screen :as screen]
            [core.screens.stage :as stage]
            [core.ctx.mouseover-entity :refer [update-mouseover-entity]]
            [core.ctx.time :as time]
            [core.ctx.potential-fields :as potential-fields]
            [core.widgets.error-modal :refer [error-window!]]
            [core.world.render.tiled-map :as world-render]
            [core.world.render.debug :as debug-render]
            [core.world.ctx :refer [active-entities]])
  (:import (com.badlogic.gdx Gdx Input$Keys)))

(def ^:private ^:dbg-flag pausing? true)

(defn- player-unpaused? []
  (or (.isKeyJustPressed Gdx/input Input$Keys/P)
      (.isKeyPressed     Gdx/input Input$Keys/SPACE)))

(defn- update-game-paused [ctx]
  (assoc ctx :context/paused? (or (:context/entity-tick-error ctx)
                                  (and pausing?
                                       (player/state-pause-game? ctx)
                                       (not (player-unpaused?))))))

(defn- update-world [ctx]
  (let [ctx (time/update-time ctx (min (.getDeltaTime Gdx/graphics) entity/max-delta-time))
        active-entities (active-entities ctx)]
    (potential-fields/update! ctx active-entities)
    (try (ecs/tick-entities! ctx active-entities)
         (catch Throwable t
           (-> ctx
               (error-window! t)
               (assoc :context/entity-tick-error t))))))

(defmulti ^:private game-loop :context/game-loop-mode)

(defmethod game-loop :game-loop/normal [ctx]
  (effect! ctx [player/update-state
                update-mouseover-entity ; this do always so can get debug info even when game not running
                update-game-paused
                #(if (:context/paused? %)
                   %
                   (update-world %))
                ecs/remove-destroyed-entities! ; do not pause this as for example pickup item, should be destroyed.
                ]))

(defn- replay-frame! [ctx]
  (let [frame-number (time/logic-frame ctx)
        txs [:foo]#_(ctx/frame->txs ctx frame-number)]
    ;(println frame-number ". " (count txs))
    (-> ctx
        (effect! txs)
        #_(update :world.time/logic-frame inc))))  ; this is probably broken now (also frame->txs contains already time, so no need to inc ?)

(def ^:private replay-speed 2)

(defmethod game-loop :game-loop/replay [ctx]
  (reduce (fn [ctx _] (replay-frame! ctx))
          ctx
          (range replay-speed)))

(defn- render-world! [ctx]
  (camera/set-position! (world-camera ctx) (:position (player-entity* ctx)))
  (world-render/render-map ctx (camera/position (world-camera ctx)))
  (render-world-view ctx
                     (fn [g]
                       (debug-render/before-entities ctx g)
                       (ecs/render-entities! ctx
                                             g
                                             (->> (active-entities ctx)
                                                  (map deref)))
                       (debug-render/after-entities ctx g))))

(defn- adjust-zoom [camera by] ; DRY map editor
  (camera/set-zoom! camera (max 0.1 (+ (camera/zoom camera) by))))

(def ^:private zoom-speed 0.05)

(defn- check-zoom-keys [ctx]
  (let [camera (world-camera ctx)]
    (when (.isKeyPressed Gdx/input Input$Keys/MINUS)  (adjust-zoom camera    zoom-speed))
    (when (.isKeyPressed Gdx/input Input$Keys/EQUALS) (adjust-zoom camera (- zoom-speed)))))

; TODO move to actor/stage listeners ? then input processor used ....
(defn- check-key-input [ctx]
  (check-zoom-keys ctx)
  (widgets/check-window-hotkeys ctx)
  (cond (and (.isKeyJustPressed Gdx/input Input$Keys/ESCAPE)
             (not (widgets/close-windows? ctx)))
        (screens/change-screen ctx :screens/options-menu)

        ; TODO not implementing StageSubScreen so NPE no screen/render!
        #_(.isKeyJustPressed Gdx/input Input$Keys/TAB)
        #_(screens/change-screen ctx :screens/minimap)

        :else
        ctx))

(defcomponent ::sub-screen
  (screen/exit [_ ctx]
    (cursors/set-cursor! ctx :cursors/default))

  (screen/render [_ ctx]
    (render-world! ctx)
    (-> ctx
        game-loop
        check-key-input)))

(derive :screens/world :screens/stage)
(defcomponent :screens/world
  (->mk [_ ctx]
    {:stage (stage/create ctx [])
     :sub-screen [::sub-screen]}))

(comment

 ; https://github.com/damn/core/issues/32
 ; grep :game-loop/replay
 ; unused code & not working
 ; record only top-lvl txs or save world state every x minutes/seconds

 ; TODO @replay-mode
 ; * do I need check-key-input from screens/world?
 ; adjust sound speed also equally ? pitch ?
 ; player message, player modals, etc. all game related state handle ....
 ; game timer is not reset  - continues as if
 ; check other atoms , try to remove atoms ...... !?
 ; replay mode no window hotkeys working
 ; buttons working
 ; can remove items from inventory ! changes cursor but does not change back ..
 ; => deactivate all input somehow (set input processor nil ?)
 ; works but ESC is separate from main input processor and on re-entry
 ; again stage is input-processor
 ; also cursor is from previous game replay
 ; => all hotkeys etc part of stage input processor make.
 ; set nil for non idle/item in hand states .
 ; for some reason he calls end of frame checks but cannot open windows with hotkeys

 (defn- start-replay-mode! [ctx]
   (.setInputProcessor Gdx/input nil)
   (init-game-context ctx :mode :game-loop/replay))

 (.postRunnable com.badlogic.gdx.Gdx/app
  (fn []
    (swap! app-state start-replay-mode!)))

 )
