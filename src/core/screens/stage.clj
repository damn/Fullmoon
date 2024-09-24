(ns core.screens.stage
  (:refer-clojure :exclude [get])
  (:require [core.ui.group :as group]
            [core.ctx :refer :all]
            [core.graphics.views :refer [gui-mouse-position]]
            [core.ctx.screens :as screens]
            [core.screen :as screen])
  (:import com.badlogic.gdx.Gdx
           com.badlogic.gdx.scenes.scene2d.Stage))

; TODO not disposed anymore... screens are sub-level.... look for dispose stuff also in @ cdq! FIXME
(defcomponent :screens/stage
  {:let {:keys [^Stage stage sub-screen]}}
  (screen/enter [_ context]
    (.setInputProcessor Gdx/input stage)
    (screen/enter sub-screen context))

  (screen/exit [_ context]
    (.setInputProcessor Gdx/input nil)
    (screen/exit sub-screen context))

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

(defn create
  "Stage implements clojure.lang.ILookup (get) on actor id."
  [{{:keys [gui-view batch]} :context/graphics} actors]
  (let [stage (->stage (:viewport gui-view) batch)]
    (run! #(.addActor stage %) actors)
    stage))

(defn get ^Stage [context]
  (:stage ((screens/current-screen context) 1)))

(defn mouse-on-actor? [context]
  (let [[x y] (gui-mouse-position context)
        touchable? true]
    (.hit (get context) x y touchable?)))

(defn add-actor! [ctx actor]
  (-> ctx get (.addActor actor))
  ctx)
