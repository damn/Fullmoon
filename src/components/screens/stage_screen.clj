(ns components.screens.stage-screen
  (:require [gdx.input :as input]
            [gdx.scene2d.stage :as stage]
            [core.scene2d.group :as group]
            [core.component :refer [defcomponent] :as component]
            [core.context :as ctx])
  (:import com.badlogic.gdx.scenes.scene2d.Stage))

; TODO not disposed anymore... screens are sub-level.... look for dispose stuff also in @ cdq! FIXME
(defcomponent :screens/stage-screen
  {:let {:keys [stage sub-screen]}}
  (component/enter [_ context]
    (input/set-processor! stage)
    (component/enter sub-screen context))

  (component/exit [_ context]
    (input/set-processor! nil)
    (component/exit sub-screen context))

  (component/render! [_ app-state]
    ; stage act first so user-screen calls change-screen -> is the end of frame
    ; otherwise would need render-after-stage
    ; or on change-screen the stage of the current screen would still .act
    (stage/act! stage)
    (swap! app-state #(component/render-ctx sub-screen %))
    (stage/draw stage)))

(defn- ->stage [viewport batch]
  (proxy [Stage clojure.lang.ILookup] [viewport batch]
    (valAt
      ([id]
       (group/find-actor-with-id (stage/root this) id))
      ([id not-found]
       (or (group/find-actor-with-id (stage/root this) id)
           not-found)))))

(extend-type core.context.Context
  core.context/Stage
  (->stage [{{:keys [gui-view batch]} :context/graphics} actors]
    (let [stage (->stage (:viewport gui-view) batch)]
      (stage/add-actors! stage actors)
      stage))

  (get-stage [context]
    (:stage ((ctx/current-screen context) 1)))

  (mouse-on-stage-actor? [context]
    (stage/hit (ctx/get-stage context)
               (ctx/gui-mouse-position context)
               :touchable? true))

  (add-to-stage! [ctx actor]
    (-> ctx
        ctx/get-stage
        (stage/add-actor! actor))
    ctx))
