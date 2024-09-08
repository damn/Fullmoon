(ns dev
  (:require [core.context :as ctx]))

(comment

 ; * Test
 ; * if z-order/effect renders behind wall
 ; * => graphics txs?
 (post-txs! [[:tx/line-render {:start [38 64]
                               :end [40 70]
                               :color [1 1 1]
                               :duration 2}]])

 (do
  (learn-skill! :skills/projectile)
  (learn-skill! :skills/spawn)
  (learn-skill! :skills/meditation)
  (learn-skill! :skills/death-ray)
  (learn-skill! :skills/convert)
  (learn-skill! :skills/blood-curse)
  (learn-skill! :skills/slow)
  (learn-skill! :skills/double-fireball))

 (create-item! :items/blood-glove)

 )


(defn- post-txs! [txs]
  (gdx.app/post-runnable
   #(swap! app/state ctx/do! txs)))

(defn learn-skill! [skill-id]
  (post-txs! (fn [ctx]
               [[:tx/add-skill
                 (:entity/id (ctx/player-entity* ctx))
                 (ctx/property ctx skill-id)]])))

(defn create-item! [item-id]
  (post-txs! (fn [ctx]
               [[:tx/item
                 (:position (ctx/player-entity* ctx))
                 (ctx/property ctx item-id)]])))
