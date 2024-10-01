(in-ns 'core.app)

(load "screens/world/debug_render")

(defn- calculate-mouseover-entity [ctx]
  (let [player-entity* (player-entity* ctx)
        hits (remove #(= (:z-order %) :z-order/effect) ; or: only items/creatures/projectiles.
                     (map deref
                          (point->entities ctx
                                           (world-mouse-position ctx))))]
    (->> render-order
         (sort-by-order hits :z-order)
         reverse
         (filter #(line-of-sight? ctx player-entity* %))
         first
         :entity/id)))

(defn- update-mouseover-entity [ctx]
  (let [entity (if (mouse-on-actor? ctx)
                 nil
                 (calculate-mouseover-entity ctx))]
    [(when-let [old-entity (:context/mouseover-entity ctx)]
       [:e/dissoc old-entity :entity/mouseover?])
     (when entity
       [:e/assoc entity :entity/mouseover? true])
     (fn [ctx]
       (assoc ctx :context/mouseover-entity entity))]))

(def ^:private ^:dbg-flag pausing? true)

(defn- player-unpaused? []
  (or (key-just-pressed? :keys/p)
      (key-pressed? :keys/space)))

(defn- update-game-paused [ctx]
  (assoc ctx :context/paused? (or (:context/entity-tick-error ctx)
                                  (and pausing?
                                       (player-state-pause-game? ctx)
                                       (not (player-unpaused?))))))
(defn- update-world [ctx]
  (let [ctx (world/update-time ctx (min (delta-time) max-delta-time))
        entities (active-entities ctx)]
    (potential-fields-update! ctx entities)
    (try (tick-entities! ctx entities)
         (catch Throwable t
           (-> ctx
               (error-window! t)
               (assoc :context/entity-tick-error t))))))

(defn- game-loop [ctx]
  (effect! ctx [player-update-state
                update-mouseover-entity ; this do always so can get debug info even when game not running
                update-game-paused
                #(if (:context/paused? %)
                   %
                   (update-world %))
                remove-destroyed-entities! ; do not pause this as for example pickup item, should be destroyed.
                ]))

(defn- render-world! [ctx]
  (camera-set-position! (world-camera ctx) (:position (player-entity* ctx)))
  (world/render-map ctx (camera-position (world-camera ctx)))
  (render-world-view ctx
                     (fn [g]
                       (before-entities ctx g)
                       (render-entities! ctx g (map deref (active-entities ctx)))
                       (after-entities ctx g))))

(defn- hotkey->window-id []
  (merge {:keys/i :inventory-window
          :keys/e :entity-info-window}
         (when (config :debug-window?)
           {:keys/z :debug-window})))

(defn- check-window-hotkeys [ctx]
  (doseq [[hotkey window-id] (hotkey->window-id)
          :when (key-just-pressed? hotkey)]
    (toggle-visible! (get (:windows (stage-get ctx)) window-id))))

(defn- close-windows?! [context]
  (let [windows (children (:windows (stage-get context)))]
    (if (some visible? windows)
      (do
       (run! #(set-visible! % false) windows)
       true))))

(defn- adjust-zoom [camera by] ; DRY map editor
  (set-zoom! camera (max 0.1 (+ (zoom camera) by))))

(def ^:private zoom-speed 0.05)

(defn- check-zoom-keys [ctx]
  (let [camera (world-camera ctx)]
    (when (key-pressed? :keys/minus)  (adjust-zoom camera    zoom-speed))
    (when (key-pressed? :keys/equals) (adjust-zoom camera (- zoom-speed)))))

; TODO move to actor/stage listeners ? then input processor used ....
(defn- check-key-input [ctx]
  (check-zoom-keys ctx)
  (check-window-hotkeys ctx)
  (cond (and (key-just-pressed? :keys/escape)
             (not (close-windows?! ctx)))
        (change-screen ctx :screens/options-menu)

        ; TODO not implementing StageSubScreen so NPE no screen-render!
        #_(key-just-pressed? :keys/tab)
        #_(change-screen ctx :screens/minimap)

        :else
        ctx))

(defcomponent :world/sub-screen
  (screen-exit [_ ctx]
    (set-cursor! ctx :cursors/default))

  (screen-render [_ ctx]
    (render-world! ctx)
    (-> ctx
        game-loop
        check-key-input)))

(derive :screens/world :screens/stage)
(defcomponent :screens/world
  (->mk [_ ctx]
    {:stage (->stage ctx [])
     :sub-screen [:world/sub-screen]}))
