(ns core.context.screens.stage-screen
  (:require [gdx.scene2d.group :as group]
            [core.component :refer [defcomponent] :as component]
            [core.context :as ctx]
            [core.context.screens :as screens]
            [core.screen :as screen]
            [core.state :as state])
  (:import com.badlogic.gdx.Gdx
           com.badlogic.gdx.scenes.scene2d.Stage))

; TODO not disposed anymore... screens are sub-level.... look for dispose stuff also in @ cdq! FIXME
(defcomponent :screens/stage-screen
  {:let {:keys [^Stage stage sub-screen]}}
  (state/enter [_ context]
    (.setInputProcessor Gdx/input stage)
    (state/enter sub-screen context))

  (state/exit [_ context]
    (.setInputProcessor Gdx/input nil)
    (state/exit sub-screen context))

  (screen/render! [_ app-state]
    ; stage act first so user-screen calls change-screen -> is the end of frame
    ; otherwise would need render-after-stage
    ; or on change-screen the stage of the current screen would still .act
    (.act stage)
    (swap! app-state #(screen/render sub-screen %))
    (.draw stage)))

(defn- ->stage ^Stage [viewport batch]
  (proxy [Stage clojure.lang.ILookup] [viewport batch]
    (valAt
      ([id]
       (group/find-actor-with-id (.getRoot ^Stage this) id))
      ([id not-found]
       (or (group/find-actor-with-id (.getRoot ^Stage this) id)
           not-found)))))

(extend-type core.context.Context
  core.context/StageScreen
  (->stage [{{:keys [gui-view batch]} :context/graphics} actors]
    (let [stage (->stage (:viewport gui-view) batch)]
      (run! #(.addActor stage %) actors)
      stage))

  (get-stage [context]
    (:stage ((screens/current-screen context) 1)))

  (mouse-on-stage-actor? [context]
    (let [[x y] (ctx/gui-mouse-position context)
          touchable? true]
      (.hit (ctx/get-stage context) x y touchable?)))

  (add-to-stage! [ctx actor]
    (-> ctx
        ctx/get-stage
        (.addActor actor))
    ctx))
