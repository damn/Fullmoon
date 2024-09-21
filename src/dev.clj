(ns dev
  (:require [core.app :as app]
            [core.context :as ctx]
            [core.entity.player :as player]
            [core.property :as property])
  (:import com.badlogic.gdx.Gdx))

(comment

 ; TODO items dont refresh on clicking tab -!

 ; * Test
 ; * if z-order/effect renders behind wall
 ; * => graphics txs?
 (post-tx! [:tx/line-render {:start [68 38]
                             :end [70 30]
                             :color [1 1 1]
                             :duration 2}])

 (do
  (learn-skill! :skills/projectile)
  (learn-skill! :skills/spawn)
  (learn-skill! :skills/meditation)
  (learn-skill! :skills/death-ray)
  (learn-skill! :skills/convert)
  (learn-skill! :skills/blood-curse)
  (learn-skill! :skills/slow)
  (learn-skill! :skills/double-fireball))

 ; FIXME
 ; first says inventory is full
 ; ok! beholder doesn't have inventory !
 ; => tests...
 (create-item! :items/blood-glove)

 (require '[clojure.string :as str])
 (spit "item_tags.txt"
       (with-out-str
        (clojure.pprint/pprint
         (distinct
          (sort
           (mapcat
            (comp #(str/split % #"-")
                  name
                  :property/id)
            (property/all-properties @app/state :properties/items)))))))

 )


(defn- post-tx! [tx]
  (.postRunnable Gdx/app #(swap! app/state ctx/do! [tx])))

(defn learn-skill! [skill-id]
  (post-tx! (fn [ctx]
              [[:tx/add-skill
                (:entity/id (player/entity* ctx))
                (property/build ctx skill-id)]])))

(defn create-item! [item-id]
  (post-tx! (fn [ctx]
              [[:tx/item
                (:position (player/entity* ctx))
                (property/build ctx item-id)]])))
