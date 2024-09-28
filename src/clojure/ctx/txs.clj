(in-ns 'clojure.ctx)

; => every tx calls a function (anyway move to the code logic, keep wiring separate ... ?)
; can make it a one-liner
; or even def-txs?
; or even mark the function with a metadata
; omg
; then components will disappear too ....???
; but then multimethod redeffing doesnt work -> can use vars in development ?

(defn create-vs
  "Creates a map for every component with map entries `[k (->mk [k v] ctx)]`."
  [components ctx]
  (reduce (fn [m [k v]]
            (assoc m k (->mk [k v] ctx)))
          {}
          components))

(defcomponent :e/create
  (do! [[_ position body components] ctx]
    (assert (and (not (contains? components :position))
                 (not (contains? components :entity/id))
                 (not (contains? components :entity/uid))))
    (let [eid (atom nil)]
      (reset! eid (-> body
                      (assoc :position position)
                      ->Body
                      (safe-merge (-> components
                                      (assoc :entity/id eid
                                             :entity/uid (unique-number!))
                                      (create-vs ctx)))))
      (create-e-system eid))))

(defcomponent :e/destroy
  (do! [[_ entity] ctx]
    [[:e/assoc entity :entity/destroyed? true]]))

(defcomponent :e/assoc
  (do! [[_ entity k v] ctx]
    (assert (keyword? k))
    (swap! entity assoc k v)
    ctx))

(defcomponent :e/assoc-in
  (do! [[_ entity ks v] ctx]
    (swap! entity assoc-in ks v)
    ctx))

(defcomponent :e/dissoc
  (do! [[_ entity k] ctx]
    (assert (keyword? k))
    (swap! entity dissoc k)
    ctx))

(defcomponent :e/dissoc-in
  (do! [[_ entity ks] ctx]
    (assert (> (count ks) 1))
    (swap! entity update-in (drop-last ks) dissoc (last ks))
    ctx))

(defcomponent :e/update-in
  (do! [[_ entity ks f] ctx]
    (swap! entity update-in ks f)
    ctx))

(defcomponent :tx/line-render
  (do! [[_ {:keys [start end duration color thick?]}] _ctx]
    [[:e/create
      start
      effect-body-props
      #:entity {:line-render {:thick? thick? :end end :color color}
                :delete-after-duration duration}]]))

(defcomponent :tx/set-movement
  (do! [[_ entity movement] ctx]
    (assert (or (nil? movement)
                (nil? (:direction movement))
                (and (:direction movement) ; continue schema of that ...
                     #_(:speed movement)))) ; princess no stats/movement-speed, then nil and here assertion-error
    [(if (or (nil? movement)
             (nil? (:direction movement)))
       [:e/dissoc entity :entity/movement]
       [:e/assoc entity :entity/movement movement])]))

(defcomponent :tx/shout
  (do! [[_ position faction delay-seconds] ctx]
    [[:e/create
      position
      effect-body-props
      {:entity/alert-friendlies-after-duration
       {:counter (->counter ctx delay-seconds)
        :faction faction}}]]))

(defcomponent :tx/add-text-effect
  (do! [[_ entity text] ctx]
    [[:e/assoc
      entity
      :entity/string-effect
      (if-let [string-effect (:entity/string-effect @entity)]
        (-> string-effect
            (update :text str "\n" text)
            (update :counter #(reset ctx %)))
        {:text text
         :counter (->counter ctx 0.4)})]]))

(defcomponent :tx/msg-to-player
  (do! [[_ message] ctx]
    (assoc ctx :context/msg-to-player {:message message :counter 0})))

(defcomponent :tx/sound
  {:data :sound}
  (do! [[_ file] ctx]
    (play-sound! ctx file)))

(defcomponent :tx/cursor
  (do! [[_ cursor-key] ctx]
    (set-cursor! ctx cursor-key)
    ctx))

(defcomponent :tx/player-modal
  (do! [[_ params] ctx]
    (show-player-modal! ctx params)))

(defcomponent :tx.entity.stats/pay-mana-cost
  (do! [[_ entity cost] _ctx]
    (let [mana-val ((entity-stat @entity :stats/mana) 0)]
      (assert (<= cost mana-val))
      [[:e/assoc-in entity [:entity/stats :stats/mana 0] (- mana-val cost)]])))

(comment
 (let [mana-val 4
       entity (atom (map->Entity {:entity/stats {:stats/mana [mana-val 10]}}))
       mana-cost 3
       resulting-mana (- mana-val mana-cost)]
   (= (do! [:tx.entity.stats/pay-mana-cost entity mana-cost] nil)
      [[:e/assoc-in entity [:entity/stats :stats/mana 0] resulting-mana]]))
 )
