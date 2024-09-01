(ns dev
  (:require [core.context :as ctx]))

(comment

 (gdx.app/post-runnable (fn []
                          (do
                           (learn-skill! :skills/projectile)
                           (learn-skill! :skills/spawn)
                           (learn-skill! :skills/meditation)
                           (learn-skill! :skills/death-ray)
                           (learn-skill! :skills/convert)
                           (learn-skill! :skills/blood-curse)
                           (learn-skill! :skills/slow)
                           (learn-skill! :skills/double-fireball))))

 (create-item! :items/blood-glove)

 )

(defn- do-on-ctx! [tx-fn]
  (swap! app/state ctx/do! [(tx-fn @app/state)]))

(defn learn-skill! [skill-id]
  (do-on-ctx! (fn [ctx]
                [:tx/add-skill
                 (:entity/id (ctx/player-entity* ctx))
                 (ctx/property ctx skill-id)])))

(defn create-item! [item-id]
  (do-on-ctx! (fn [ctx]
                [:tx/item
                 (:position (ctx/player-entity* ctx))
                 (ctx/property ctx item-id)])))
