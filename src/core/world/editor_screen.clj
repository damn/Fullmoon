(in-ns 'core.world)

; TODO map-coords are clamped ? thats why showing 0 under and left of the map?
; make more explicit clamped-map-coords ?

; TODO
; leftest two tiles are 0 coordinate x
; and rightest is 16, not possible -> check clamping
; depends on screen resize or something, changes,
; maybe update viewport not called on resize sometimes

(defn- show-whole-map! [camera tiled-map]
  (camera-set-position! camera
                        [(/ (width  tiled-map) 2)
                         (/ (height tiled-map) 2)])
  (set-zoom! camera
             (calculate-zoom camera
                             :left [0 0]
                             :top [0 (height tiled-map)]
                             :right [(width tiled-map) 0]
                             :bottom [0 0])))

(defn- current-data [ctx]
  (-> ctx
      current-screen
      (get 1)
      :sub-screen
      (get 1)))

(def ^:private infotext
  "L: grid lines
M: movement properties
zoom: shift-left,minus
ESCAPE: leave
direction keys: move")

(defn- debug-infos ^String [ctx]
  (let [tile (->tile (world-mouse-position ctx))
        {:keys [tiled-map
                area-level-grid]} @(current-data ctx)]
    (->> [infotext
          (str "Tile " tile)
          (when-not area-level-grid
            (str "Module " (mapv (comp int /)
                                 (world-mouse-position ctx)
                                 [module-width module-height])))
          (when area-level-grid
            (str "Creature id: " (property-value tiled-map :creatures tile :id)))
          (when area-level-grid
            (let [level (get area-level-grid tile)]
              (when (number? level)
                (str "Area level:" level))))
          (str "Movement properties " (movement-property tiled-map tile) "\n"
               (apply vector (movement-properties tiled-map tile)))]
         (remove nil?)
         (str/join "\n"))))

; same as debug-window
(defn- ->info-window [ctx]
  (let [label (->label "")
        window (->window {:title "Info" :rows [[label]]})]
    (add-actor! window (->actor {:act #(do
                                              (.setText label (debug-infos %))
                                              (.pack window))}))
    (set-position! window 0 (gui-viewport-height ctx))
    window))

(defn- adjust-zoom [camera by] ; DRY context.game
  (set-zoom! camera (max 0.1 (+ (zoom camera) by))))

; TODO movement-speed scales with zoom value for big maps useful
(def ^:private camera-movement-speed 1)
(def ^:private zoom-speed 0.1)

; TODO textfield takes control !
; TODO PLUS symbol shift & = symbol on keyboard not registered
(defn- camera-controls [context camera]
  (when (key-pressed? :keys/shift-left)
    (adjust-zoom camera    zoom-speed))
  (when (key-pressed? :keys/minus)
    (adjust-zoom camera (- zoom-speed)))
  (let [apply-position (fn [idx f]
                         (camera-set-position! camera
                                               (update (camera-position camera)
                                                       idx
                                                       #(f % camera-movement-speed))))]
    (if (key-pressed? :keys/left)  (apply-position 0 -))
    (if (key-pressed? :keys/right) (apply-position 0 +))
    (if (key-pressed? :keys/up)    (apply-position 1 +))
    (if (key-pressed? :keys/down)  (apply-position 1 -))))

#_(def ^:private show-area-level-colors true)
; TODO unused
; TODO also draw numbers of area levels big as module size...

(defn- render-on-map [g ctx]
  (let [{:keys [tiled-map
                area-level-grid
                start-position
                show-movement-properties
                show-grid-lines]} @(current-data ctx)
        visible-tiles (visible-tiles (world-camera ctx))
        [x y] (->tile (world-mouse-position ctx))]
    (draw-rectangle g x y 1 1 :white)
    (when start-position
      (draw-filled-rectangle g (start-position 0) (start-position 1) 1 1 [1 0 1 0.9]))
    ; TODO move down to other doseq and make button
    (when show-movement-properties
      (doseq [[x y] visible-tiles
              :let [movement-property (movement-property tiled-map [x y])]]
        (draw-filled-circle g [(+ x 0.5) (+ y 0.5)] 0.08 :black)
        (draw-filled-circle g [(+ x 0.5) (+ y 0.5)]
                            0.05
                            (case movement-property
                              "all"   :green
                              "air"   :orange
                              "none"  :red))))
    (when show-grid-lines
      (draw-grid g 0 0 (width  tiled-map) (height tiled-map) 1 1 [1 1 1 0.5]))))

(def ^:private world-id :worlds/modules)

(defn- generate-screen-ctx [context properties]
  (let [;{:keys [tiled-map area-level-grid start-position]} (generate-modules context properties)
        {:keys [tiled-map start-position]} (generate-level context world-id)
        atom-data (current-data context)]
    (dispose! (:tiled-map @atom-data))
    (swap! atom-data assoc
           :tiled-map tiled-map
           ;:area-level-grid area-level-grid
           :start-position start-position)
    (show-whole-map! (world-camera context) tiled-map)
    (.setVisible (get-layer tiled-map "creatures") true)
    context))

(defn ->generate-map-window [ctx level-id]
  (->window {:title "Properties"
             :cell-defaults {:pad 10}
             :rows [[(->label (with-out-str
                               (clojure.pprint/pprint
                                (build-property ctx level-id))))]
                    [(->text-button "Generate" #(try (generate-screen-ctx % (build-property % level-id))
                                                     (catch Throwable t
                                                       (error-window! % t)
                                                       (println t)
                                                       %)))]]
             :pack? true}))

(defcomponent ::sub-screen
  {:let current-data}
  ; TODO ?
  #_(dispose [_]
      (dispose! (:tiled-map @current-data)))

  (screen-enter [_ ctx]
    (show-whole-map! (world-camera ctx) (:tiled-map @current-data)))

  (screen-exit [_ ctx]
    (reset-zoom! (world-camera ctx)))

  (screen-render [_ context]
    (render! context (:tiled-map @current-data) (constantly white))
    (render-world-view context #(render-on-map % context))
    (if (key-just-pressed? :keys/l)
      (swap! current-data update :show-grid-lines not))
    (if (key-just-pressed? :keys/m)
      (swap! current-data update :show-movement-properties not))
    (camera-controls context (world-camera context))
    (if (key-just-pressed? :keys/escape)
      (change-screen context :screens/main-menu)
      context)))

(derive :screens/map-editor :screens/stage)
(defcomponent :screens/map-editor
  (->mk [_ ctx]
    {:sub-screen [::sub-screen
                  (atom {:tiled-map (load-map modules-file)
                         :show-movement-properties false
                         :show-grid-lines false})]
     :stage (->stage ctx [(->generate-map-window ctx world-id)
                             (->info-window ctx)])}))
