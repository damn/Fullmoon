(ns clojure.gdx
  (:require [clojure.gdx.audio :refer [play-sound!]]
            [clojure.gdx.graphics :as g]
            [clojure.gdx.ui :as ui]
            [clojure.gdx.ui.actor :as a]
            [clojure.gdx.ui.stage-screen :refer [stage-get stage-add!]]
            [clojure.gdx.math.shape :as shape]
            [clojure.gdx.math.vector :as v]
            [clojure.string :as str]
            [clj-commons.pretty.repl :refer [pretty-pst]]
            [core.component :refer [defsystem defc] :as component]
            [core.data :as data]
            [core.effect :refer [do! effect!]]
            [core.operation :as op]
            [core.db :as db]
            [core.property :as property]
            [data.grid2d :as g2d]
            [malli.core :as m]
            [utils.core :refer [bind-root find-first ->tile safe-merge readable-number]]
            [world.creature.faction :as faction]
            [world.grid :as grid :refer [world-grid]]
            [world.raycaster :refer [ray-blocked?]]))

(def ^:private image-file "images/moon_background.png")

(defn ->background-image []
  (ui/image->widget (g/image image-file)
                    {:fill-parent? true
                     :scaling :fill
                     :align :center}))

(defmacro ^:private with-err-str
  "Evaluates exprs in a context in which *err* is bound to a fresh
  StringWriter.  Returns the string created by any nested printing
  calls."
  [& body]
  `(let [s# (new java.io.StringWriter)]
     (binding [*err* s#]
       ~@body
       (str s#))))

(defn error-window! [throwable]
  (binding [*print-level* 5]
    (pretty-pst throwable 24))
  (stage-add! (ui/window {:title "Error"
                          :rows [[(ui/label (binding [*print-level* 3]
                                             (with-err-str
                                               (clojure.repl/pst throwable))))]]
                          :modal? true
                          :close-button? true
                          :close-on-escape? true
                          :center? true
                          :pack? true})))

(declare world-paused?
         world-player)

(declare content-grid)

(defn content-grid-update-entity! [entity]
  (let [{:keys [grid cell-w cell-h]} content-grid
        {::keys [content-cell] :as entity*} @entity
        [x y] (:position entity*)
        new-cell (get grid [(int (/ x cell-w))
                            (int (/ y cell-h))])]
    (when-not (= content-cell new-cell)
      (swap! new-cell update :entities conj entity)
      (swap! entity assoc ::content-cell new-cell)
      (when content-cell
        (swap! content-cell update :entities disj entity)))))

(defn content-grid-remove-entity! [entity]
  (-> @entity
      ::content-cell
      (swap! update :entities disj entity)))

(defn- active-entities* [center-entity*]
  (let [{:keys [grid]} content-grid]
    (->> (let [idx (-> center-entity*
                       ::content-cell
                       deref
                       :idx)]
           (cons idx (g2d/get-8-neighbour-positions idx)))
         (keep grid)
         (mapcat (comp :entities deref)))))

(defn active-entities []
  (active-entities* @world-player))

(defn init-content-grid! [& {:keys [cell-size width height]}]
  (bind-root
   #'content-grid
   {:grid (g2d/create-grid (inc (int (/ width  cell-size))) ; inc because corners
                           (inc (int (/ height cell-size)))
                           (fn [idx]
                             (atom {:idx idx,
                                    :entities #{}})))
    :cell-w cell-size
    :cell-h cell-size}))

(def mouseover-entity nil)

(defn mouseover-entity* []
  (when-let [entity mouseover-entity]
    @entity))

(declare explored-tile-corners)

(declare ^{:doc "The game logic update delta-time. Different then delta-time-raw because it is bounded by a maximum value for entity movement speed."}
         world-delta
         ^{:doc "The elapsed in-game-time (not counting when game is paused)."}
         elapsed-time
         ^{:doc "The game-logic frame number, starting with 1. (not counting when game is paused)"}
         logic-frame)

(defn init-world-time! []
  (bind-root #'elapsed-time 0)
  (bind-root #'logic-frame 0))

(defn update-time [delta]
  (bind-root #'world-delta delta)
  (alter-var-root #'elapsed-time + delta)
  (alter-var-root #'logic-frame inc))

(defrecord Counter [duration stop-time])

(defn ->counter [duration]
  {:pre [(>= duration 0)]}
  (->Counter duration (+ elapsed-time duration)))

(defn stopped? [{:keys [stop-time]}]
  (>= elapsed-time stop-time))

(defn reset [{:keys [duration] :as counter}]
  (assoc counter :stop-time (+ elapsed-time duration)))

(defn finished-ratio [{:keys [duration stop-time] :as counter}]
  {:post [(<= 0 % 1)]}
  (if (stopped? counter)
    0
    ; min 1 because floating point math inaccuracies
    (min 1 (/ (- stop-time elapsed-time) duration))))

(defn- define-order [order-k-vector]
  (apply hash-map (interleave order-k-vector (range))))

(defn sort-by-order [coll get-item-order-k order]
  (sort-by #((get-item-order-k %) order) < coll))

#_(defn order-contains? [order k]
  ((apply hash-set (keys order)) k))

#_(deftest test-order
  (is
    (= (define-order [:a :b :c]) {:a 0 :b 1 :c 2}))
  (is
    (order-contains? (define-order [:a :b :c]) :a))
  (is
    (not
      (order-contains? (define-order [:a :b :c]) 2)))
  (is
    (=
      (sort-by-order [:c :b :a :b] identity (define-order [:a :b :c]))
      '(:a :b :b :c)))
  (is
    (=
      (sort-by-order [:b :c :null :null :a] identity (define-order [:c :b :a :null]))
      '(:c :b :a :null :null))))

;;;; ?

; java.lang.IllegalArgumentException: No method in multimethod 'render-info' for dispatch value: :position
; actually we dont want this to be called over that
; it should be :components? then ?
; => shouldn't need default fns for render -> don't call it if its not there

; every component has parent-entity-id (peid)
; fetch active entity-ids
; then fetch all components which implement render-below
; and have parent-id in entity-ids, etc.

;;;; Body

; so that at low fps the game doesn't jump faster between frames used @ movement to set a max speed so entities don't jump over other entities when checking collisions
(def max-delta-time 0.04)

; setting a min-size for colliding bodies so movement can set a max-speed for not
; skipping bodies at too fast movement
; TODO assert at properties load
(def ^:private min-solid-body-size 0.39) ; == spider smallest creature size.

; set max speed so small entities are not skipped by projectiles
; could set faster than max-speed if I just do multiple smaller movement steps in one frame
(def ^:private max-speed (/ min-solid-body-size max-delta-time)) ; need to make var because m/schema would fail later if divide / is inside the schema-form
(def movement-speed-schema (m/schema [:and number? [:>= 0] [:<= max-speed]]))

(def hpbar-height-px 5)

(def ^:private z-orders [:z-order/on-ground
                         :z-order/ground
                         :z-order/flying
                         :z-order/effect])

(def render-order (define-order z-orders))

(defrecord Entity [position
                   left-bottom
                   width
                   height
                   half-width
                   half-height
                   radius
                   collides?
                   z-order
                   rotation-angle])

(defn- ->Body [{[x y] :position
                :keys [position
                       width
                       height
                       collides?
                       z-order
                       rotation-angle]}]
  (assert position)
  (assert width)
  (assert height)
  (assert (>= width  (if collides? min-solid-body-size 0)))
  (assert (>= height (if collides? min-solid-body-size 0)))
  (assert (or (boolean? collides?) (nil? collides?)))
  (assert ((set z-orders) z-order))
  (assert (or (nil? rotation-angle)
              (<= 0 rotation-angle 360)))
  (map->Entity
   {:position (mapv float position)
    :left-bottom [(float (- x (/ width  2)))
                  (float (- y (/ height 2)))]
    :width  (float width)
    :height (float height)
    :half-width  (float (/ width  2))
    :half-height (float (/ height 2))
    :radius (float (max (/ width  2)
                        (/ height 2)))
    :collides? collides?
    :z-order z-order
    :rotation-angle (or rotation-angle 0)}))

(def ^{:doc "For effects just to have a mouseover body size for debugging purposes."}
  effect-body-props
  {:width 0.5
   :height 0.5
   :z-order :z-order/effect})

(defn direction [entity* other-entity*]
  (v/direction (:position entity*) (:position other-entity*)))

(defn collides? [entity* other-entity*]
  (shape/overlaps? entity* other-entity*))

;;;; ?

(defprotocol State
  (entity-state [_])
  (state-obj [_]))

(defprotocol Inventory
  (can-pickup-item? [_ item]))

(defprotocol Stats
  (entity-stat [_ stat] "Calculating value of the stat w. modifiers"))

(defprotocol Modifiers
  (->modified-value [_ modifier-k base-value]))

;;;; line-of-sight

(require '[clojure.gdx.graphics.camera :as 🎥])

; does not take into account zoom - but zoom is only for debug ???
; vision range?
(defn- on-screen? [entity*]
  (let [[x y] (:position entity*)
        x (float x)
        y (float y)
        [cx cy] (🎥/position (g/world-camera))
        px (float cx)
        py (float cy)
        xdist (Math/abs (- x px))
        ydist (Math/abs (- y py))]
    (and
     (<= xdist (inc (/ (float (g/world-viewport-width))  2)))
     (<= ydist (inc (/ (float (g/world-viewport-height)) 2))))))

; TODO at wrong point , this affects targeting logic of npcs
; move the debug flag to either render or mouseover or lets see
(def ^:private ^:dbg-flag los-checks? true)

; does not take into account size of entity ...
; => assert bodies <1 width then
(defn line-of-sight? [source* target*]
  (and (or (not (:entity/player? source*))
           (on-screen? target*))
       (not (and los-checks?
                 (ray-blocked? (:position source*) (:position target*))))))

(defsystem create "Create entity with eid for txs side-effects. Default nil." [_ entity])
(defmethod create :default [_ entity])

(defsystem destroy [_ entity])
(defmethod destroy :default [_ entity])

(defsystem tick [_ entity])
(defmethod tick :default [_ entity])

(defsystem render-below [_ entity*])
(defmethod render-below :default [_ entity*])

(defsystem render [_ entity*])
(defmethod render :default [_ entity*])

(defsystem render-above [_ entity*])
(defmethod render-above :default [_ entity*])

(defsystem render-info [_ entity*])
(defmethod render-info :default [_ entity*])

(def ^:private render-systems [render-below
                               render
                               render-above
                               render-info])

(declare ^:private uids-entities)

(defn init-uids-entities! []
  (bind-root #'uids-entities {}))

(defn all-entities [] (vals uids-entities))

(defn get-entity
  "Mostly used for debugging, use an entity's atom for (probably) faster access in your logic."
  [uid]
  (get uids-entities uid))

(defc :entity/id
  (create  [[_ id] _eid] [[:tx/add-to-world      id]])
  (destroy [[_ id] _eid] [[:tx/remove-from-world id]]))

(defc :entity/uid
  {:let uid}
  (create [_ entity]
    (assert (number? uid))
    (alter-var-root #'uids-entities assoc uid entity)
    nil)

  (destroy [_ _entity]
    (assert (contains? uids-entities uid))
    (alter-var-root #'uids-entities dissoc uid)
    nil))

(let [cnt (atom 0)]
  (defn- unique-number! []
    (swap! cnt inc)))

(defn- create-e-system [eid]
  (for [component @eid]
    (fn []
      ; we are assuming components dont remove other ones at entity/create
      ; thats why we reuse component and not fetch each time again for key
      (create component eid))))

(defc :e/create
  (do! [[_ position body components]]
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
                                      (component/create-vs)))))
      (create-e-system eid))))

(defc :e/destroy
  (do! [[_ entity]]
    [[:e/assoc entity :entity/destroyed? true]]))

(defc :e/assoc
  (do! [[_ entity k v]]
    (assert (keyword? k))
    (swap! entity assoc k v)
    nil))

(defc :e/assoc-in
  (do! [[_ entity ks v]]
    (swap! entity assoc-in ks v)
    nil))

(defc :e/dissoc
  (do! [[_ entity k]]
    (assert (keyword? k))
    (swap! entity dissoc k)
    nil))

(defc :e/dissoc-in
  (do! [[_ entity ks]]
    (assert (> (count ks) 1))
    (swap! entity update-in (drop-last ks) dissoc (last ks))
    nil))

(defc :e/update-in
  (do! [[_ entity ks f]]
    (swap! entity update-in ks f)
    nil))

(def ^:private ^:dbg-flag show-body-bounds false)

(defn- draw-body-rect [entity* color]
  (let [[x y] (:left-bottom entity*)]
    (g/draw-rectangle x y (:width entity*) (:height entity*) color)))

(defn- render-entity* [system entity*]
  (try
   (when show-body-bounds
     (draw-body-rect entity* (if (:collides? entity*) :white :gray)))
   (run! #(system % entity*) entity*)
   (catch Throwable t
     (draw-body-rect entity* :red)
     (pretty-pst t 12))))

; precaution in case a component gets removed by another component
; the question is do we still want to update nil components ?
; should be contains? check ?
; but then the 'order' is important? in such case dependent components
; should be moved together?
(defn- tick-system [entity]
  (try
   (doseq [k (keys @entity)]
     (when-let [v (k @entity)]
       (effect! (tick [k v] entity))))
   (catch Throwable t
     (throw (ex-info "" (select-keys @entity [:entity/uid]) t)))))

(defn tick-entities!
  "Calls tick system on all components of entities."
  [entities]
  (run! tick-system entities))

(defn render-entities!
  "Draws entities* in the correct z-order and in the order of render-systems for each z-order."
  [entities*]
  (let [player-entity* @world-player]
    (doseq [[z-order entities*] (sort-by-order (group-by :z-order entities*)
                                               first
                                               render-order)
            system render-systems
            entity* entities*
            :when (or (= z-order :z-order/effect)
                      (line-of-sight? player-entity* entity*))]
      (render-entity* system entity*))))

(defn remove-destroyed-entities!
  "Calls destroy on all entities which are marked with ':e/destroy'"
  []
  (for [entity (filter (comp :entity/destroyed? deref) (all-entities))
        component @entity]
    (fn []
      (destroy component entity))))

(defc :entity/image
  {:data :image
   :let image}
  (render [_ entity*]
    (g/draw-rotated-centered-image image
                                   (or (:rotation-angle entity*) 0)
                                   (:position entity*))))

(defprotocol Animation
  (^:private anim-tick [_ delta])
  (^:private restart [_])
  (^:private anim-stopped? [_])
  (^:private current-frame [_]))

(defrecord ImmutableAnimation [frames frame-duration looping? cnt maxcnt]
  Animation
  (anim-tick [this delta]
    (let [maxcnt (float maxcnt)
          newcnt (+ (float cnt) (float delta))]
      (assoc this :cnt (cond (< newcnt maxcnt) newcnt
                             looping? (min maxcnt (- newcnt maxcnt))
                             :else maxcnt))))

  (restart [this]
    (assoc this :cnt 0))

  (anim-stopped? [_]
    (and (not looping?) (>= cnt maxcnt)))

  (current-frame [this]
    (frames (min (int (/ (float cnt) (float frame-duration)))
                 (dec (count frames))))))

(defn- ->animation [frames & {:keys [frame-duration looping?]}]
  (map->ImmutableAnimation
    {:frames (vec frames)
     :frame-duration frame-duration
     :looping? looping?
     :cnt 0
     :maxcnt (* (count frames) (float frame-duration))}))

(defn- edn->animation [{:keys [frames frame-duration looping?]}]
  (->animation (map g/edn->image frames)
               :frame-duration frame-duration
               :looping? looping?))


(defmethod db/edn->value :data/animation [_ animation]
  (edn->animation animation))

(defn- tx-assoc-image-current-frame [eid animation]
  [:e/assoc eid :entity/image (current-frame animation)])

(defc :entity/animation
  {:data :data/animation
   :let animation}
  (create [_ eid]
    [(tx-assoc-image-current-frame eid animation)])

  (tick [[k _] eid]
    [(tx-assoc-image-current-frame eid animation)
     [:e/assoc eid k (anim-tick animation world-delta)]]))

(defc :entity/delete-after-animation-stopped?
  (create [_ entity]
    (-> @entity :entity/animation :looping? not assert))

  (tick [_ entity]
    (when (anim-stopped? (:entity/animation @entity))
      [[:e/destroy entity]])))

(property/def :properties/audiovisuals
  {:schema [:tx/sound
            :entity/animation]
   :overview {:title "Audiovisuals"
              :columns 10
              :image/scale 2}})

(defc :tx/audiovisual
  (do! [[_ position id]]
    (let [{:keys [tx/sound
                  entity/animation]} (db/get id)]
      [[:tx/sound sound]
       [:e/create
        position
        effect-body-props
        {:entity/animation animation
         :entity/delete-after-animation-stopped? true}]])))

(defn- move-position [position {:keys [direction speed delta-time]}]
  (mapv #(+ %1 (* %2 speed delta-time)) position direction))

(defn- move-body [body movement]
  (-> body
      (update :position    move-position movement)
      (update :left-bottom move-position movement)))

(defn- valid-position? [{:keys [entity/id z-order] :as body}]
  {:pre [(:collides? body)]}
  (let [cells* (into [] (map deref) (grid/rectangle->cells world-grid body))]
    (and (not-any? #(grid/blocked? % z-order) cells*)
         (->> cells*
              grid/cells->entities
              (not-any? (fn [other-entity]
                          (let [other-entity* @other-entity]
                            (and (not= (:entity/id other-entity*) id)
                                 (:collides? other-entity*)
                                 (collides? other-entity* body)))))))))

(defn- try-move [body movement]
  (let [new-body (move-body body movement)]
    (when (valid-position? new-body)
      new-body)))

; TODO sliding threshold
; TODO name - with-sliding? 'on'
; TODO if direction was [-1 0] and invalid-position then this algorithm tried to move with
; direection [0 0] which is a waste of processor power...
(defn- try-move-solid-body [body {[vx vy] :direction :as movement}]
  (let [xdir (Math/signum (float vx))
        ydir (Math/signum (float vy))]
    (or (try-move body movement)
        (try-move body (assoc movement :direction [xdir 0]))
        (try-move body (assoc movement :direction [0 ydir])))))

(defc :entity/movement
  {:let {:keys [direction speed rotate-in-movement-direction?] :as movement}}
  (tick [_ eid]
    (assert (m/validate movement-speed-schema speed))
    (assert (or (zero? (v/length direction))
                (v/normalised? direction)))
    (when-not (or (zero? (v/length direction))
                  (nil? speed)
                  (zero? speed))
      (let [movement (assoc movement :delta-time world-delta)
            body @eid]
        (when-let [body (if (:collides? body) ; < == means this is a movement-type ... which could be a multimethod ....
                          (try-move-solid-body body movement)
                          (move-body body movement))]
          [[:e/assoc eid :position    (:position    body)]
           [:e/assoc eid :left-bottom (:left-bottom body)]
           (when rotate-in-movement-direction?
             [:e/assoc eid :rotation-angle (v/angle-from-vector direction)])
           [:tx/position-changed eid]])))))

(defc :tx/set-movement
  (do! [[_ entity movement]]
    (assert (or (nil? movement)
                (nil? (:direction movement))
                (and (:direction movement) ; continue schema of that ...
                     #_(:speed movement)))) ; princess no stats/movement-speed, then nil and here assertion-error
    [(if (or (nil? movement)
             (nil? (:direction movement)))
       [:e/dissoc entity :entity/movement]
       [:e/assoc entity :entity/movement movement])]))

(defc :entity/delete-after-duration
  {:let counter}
  (component/create [[_ duration]]
    (->counter duration))

  (component/info [_]
    (str "[LIGHT_GRAY]Remaining: " (readable-number (finished-ratio counter)) "/1[]"))

  (tick [_ eid]
    (when (stopped? counter)
      [[:e/destroy eid]])))

(defc :entity/destroy-audiovisual
  {:let audiovisuals-id}
  (destroy [_ entity]
    [[:tx/audiovisual (:position @entity) audiovisuals-id]]))

(defc :entity/line-render
  {:let {:keys [thick? end color]}}
  (render [_ entity*]
    (let [position (:position entity*)]
      (if thick?
        (g/with-shape-line-width 4 #(g/draw-line position end color))
        (g/draw-line position end color)))))

(defc :tx/line-render
  (do! [[_ {:keys [start end duration color thick?]}]]
    [[:e/create
      start
      effect-body-props
      #:entity {:line-render {:thick? thick? :end end :color color}
                :delete-after-duration duration}]]))

(property/def :properties/skills
  {:schema [:entity/image
            :property/pretty-name
            :skill/action-time-modifier-key
            :skill/action-time
            :skill/start-action-sound
            :skill/effects
            [:skill/cooldown {:optional true}]
            [:skill/cost {:optional true}]]
   :overview {:title "Skills"
              :columns 16
              :image/scale 2}})

(defsystem clicked-skillmenu-skill [_ skill])
(defmethod clicked-skillmenu-skill :default [_ skill])

(defn- player-clicked-skillmenu [skill]
  (clicked-skillmenu-skill (state-obj @world-player) skill))

; TODO render text label free-skill-points
; (str "Free points: " (:entity/free-skill-points @world-player))
#_(defn ->skill-window []
    (ui/window {:title "Skills"
                :id :skill-window
                :visible? false
                :cell-defaults {:pad 10}
                :rows [(for [id [:skills/projectile
                                 :skills/meditation
                                 :skills/spawn
                                 :skills/melee-attack]
                             :let [; get-property in callbacks if they get changed, this is part of context permanently
                                   button (ui/image-button ; TODO reuse actionbar button scale?
                                                           (:entity/image (db/get id)) ; TODO here anyway taken
                                                           ; => should probably build this window @ game start
                                                           (fn []
                                                             (effect! (player-clicked-skillmenu (db/get id)))))]]
                         (do
                          (ui/add-tooltip! button #(component/info-text (db/get id))) ; TODO no player modifiers applied (see actionbar)
                          button))]
                :pack? true}))

(defc :skill/action-time {:data :pos}
  (component/info [[_ v]]
    (str "[GOLD]Action-Time: " (readable-number v) " seconds[]")))

(defc :skill/cooldown {:data :nat-int}
  (component/info [[_ v]]
    (when-not (zero? v)
      (str "[SKY]Cooldown: " (readable-number v) " seconds[]"))))

(defc :skill/cost {:data :nat-int}
  (component/info [[_ v]]
    (when-not (zero? v)
      (str "[CYAN]Cost: " v " Mana[]"))))

(defc :skill/effects
  {:data [:components-ns :effect]})

(defc :skill/start-action-sound {:data :sound})

(defc :skill/action-time-modifier-key
  {:data [:enum :stats/cast-speed :stats/attack-speed]}
  (component/info [[_ v]]
    (str "[VIOLET]" (case v
                      :stats/cast-speed "Spell"
                      :stats/attack-speed "Attack") "[]")))

(defc :entity/skills
  {:data [:one-to-many :properties/skills]}
  (create [[k skills] eid]
    (cons [:e/assoc eid k nil]
          (for [skill skills]
            [:tx/add-skill eid skill])))

  (component/info [[_ skills]]
    ; => recursive info-text leads to endless text wall
    #_(when (seq skills)
        (str "[VIOLET]Skills: " (str/join "," (map name (keys skills))) "[]")))

  (tick [[k skills] eid]
    (for [{:keys [skill/cooling-down?] :as skill} (vals skills)
          :when (and cooling-down?
                     (stopped? cooling-down?))]
      [:e/assoc-in eid [k (:property/id skill) :skill/cooling-down?] false])))

(defn has-skill? [{:keys [entity/skills]} {:keys [property/id]}]
  (contains? skills id))

(defc :tx/add-skill
  (do! [[_ entity {:keys [property/id] :as skill}]]
    (assert (not (has-skill? @entity skill)))
    [[:e/assoc-in entity [:entity/skills id] skill]
     (when (:entity/player? @entity)
       [:tx.action-bar/add skill])]))

(defc :tx/remove-skill
  (do! [[_ entity {:keys [property/id] :as skill}]]
    (assert (has-skill? @entity skill))
    [[:e/dissoc-in entity [:entity/skills id]]
     (when (:entity/player? @entity)
       [:tx.action-bar/remove skill])]))

(defc :tx.entity.stats/pay-mana-cost
  (do! [[_ entity cost]]
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

(defc :entity/clickable
  (render [[_ {:keys [text]}]
           {:keys [entity/mouseover?] :as entity*}]
    (when (and mouseover? text)
      (let [[x y] (:position entity*)]
        (g/draw-text {:text text
                      :x x
                      :y (+ y (:half-height entity*))
                      :up? true})))))

(def ^:private outline-alpha 0.4)
(def ^:private enemy-color    [1 0 0 outline-alpha])
(def ^:private friendly-color [0 1 0 outline-alpha])
(def ^:private neutral-color  [1 1 1 outline-alpha])

(defc :entity/mouseover?
  (render-below [_ {:keys [entity/faction] :as entity*}]
    (let [player-entity* @world-player]
      (g/with-shape-line-width 3
        #(g/draw-ellipse (:position entity*)
                         (:half-width entity*)
                         (:half-height entity*)
                         (cond (= faction (faction/enemy player-entity*))
                               enemy-color
                               (= faction (faction/friend player-entity*))
                               friendly-color
                               :else
                               neutral-color))))))

(def ^:private shout-radius 4)

(defn- friendlies-in-radius [position faction]
  (->> {:position position
        :radius shout-radius}
       (grid/circle->entities world-grid)
       (map deref)
       (filter #(= (:entity/faction %) faction))
       (map :entity/id)))

(defc :entity/alert-friendlies-after-duration
  {:let {:keys [counter faction]}}
  (tick [_ eid]
    (when (stopped? counter)
      (cons [:e/destroy eid]
            (for [friendly-eid (friendlies-in-radius (:position @eid) faction)]
              [:tx/event friendly-eid :alert])))))

(defc :tx/shout
  (do! [[_ position faction delay-seconds]]
    [[:e/create
      position
      effect-body-props
      {:entity/alert-friendlies-after-duration
       {:counter (->counter delay-seconds)
        :faction faction}}]]))

(defc :entity/string-effect
  (tick [[k {:keys [counter]}] eid]
    (when (stopped? counter)
      [[:e/dissoc eid k]]))

  (render-above [[_ {:keys [text]}] entity*]
    (let [[x y] (:position entity*)]
      (g/draw-text {:text text
                    :x x
                    :y (+ y (:half-height entity*) (g/pixels->world-units hpbar-height-px))
                    :scale 2
                    :up? true}))))

(defc :tx/add-text-effect
  (do! [[_ entity text]]
    [[:e/assoc
      entity
      :entity/string-effect
      (if-let [string-effect (:entity/string-effect @entity)]
        (-> string-effect
            (update :text str "\n" text)
            (update :counter reset))
        {:text text
         :counter (->counter 0.4)})]]))

(defn- txs-update-modifiers [entity modifiers f]
  (for [[modifier-k operations] modifiers
        [operation-k value] operations]
    [:e/update-in entity [:entity/modifiers modifier-k operation-k] (f value)]))

(defn- conj-value [value]
  (fn [values]
    (conj values value)))

(defn- remove-one [coll item]
  (let [[n m] (split-with (partial not= item) coll)]
    (concat n (rest m))))

(defn- remove-value [value]
  (fn [values]
    {:post [(= (count %) (dec (count values)))]}
    (remove-one values value)))

(defc :tx/apply-modifiers
  (do! [[_ entity modifiers]]
    (txs-update-modifiers entity modifiers conj-value)))

(defc :tx/reverse-modifiers
  (do! [[_ entity modifiers]]
    (txs-update-modifiers entity modifiers remove-value)))

(comment
 (= (txs-update-modifiers :entity
                         {:modifier/hp {:op/max-inc 5
                                        :op/max-mult 0.3}
                          :modifier/movement-speed {:op/mult 0.1}}
                         (fn [_value] :fn))
    [[:e/update-in :entity [:entity/modifiers :modifier/hp :op/max-inc] :fn]
     [:e/update-in :entity [:entity/modifiers :modifier/hp :op/max-mult] :fn]
     [:e/update-in :entity [:entity/modifiers :modifier/movement-speed :op/mult] :fn]])
 )

; DRY ->effective-value (summing)
; also: sort-by op/order @ modifier/info-text itself (so player will see applied order)
(defn- sum-operation-values [modifiers]
  (for [[modifier-k operations] modifiers
        :let [operations (for [[operation-k values] operations
                               :let [value (apply + values)]
                               :when (not (zero? value))]
                           [operation-k value])]
        :when (seq operations)]
    [modifier-k operations]))

(g/def-markup-color "MODIFIER_BLUE" :cyan)

; For now no green/red color for positive/negative numbers
; as :stats/damage-receive negative value would be red but actually a useful buff
; -> could give damage reduce 10% like in diablo 2
; and then make it negative .... @ applicator
(def ^:private positive-modifier-color "[MODIFIER_BLUE]" #_"[LIME]")
(def ^:private negative-modifier-color "[MODIFIER_BLUE]" #_"[SCARLET]")

(defn k->pretty-name [k]
  (str/capitalize (name k)))

(defn mod-info-text [modifiers]
  (str "[MODIFIER_BLUE]"
       (str/join "\n"
                 (for [[modifier-k operations] modifiers
                       operation operations]
                   (str (op/info-text operation) " " (k->pretty-name modifier-k))))
       "[]"))

(defc :entity/modifiers
  {:data [:components-ns :modifier]
   :let modifiers}
  (component/create [_]
    (into {} (for [[modifier-k operations] modifiers]
               [modifier-k (into {} (for [[operation-k value] operations]
                                      [operation-k [value]]))])))

  (component/info [_]
    (let [modifiers (sum-operation-values modifiers)]
      (when (seq modifiers)
        (mod-info-text modifiers)))))

(extend-type clojure.gdx.Entity
  Modifiers
  (->modified-value [{:keys [entity/modifiers]} modifier-k base-value]
    {:pre [(= "modifier" (namespace modifier-k))]}
    (->> modifiers
         modifier-k
         (sort-by op/order)
         (reduce (fn [base-value [operation-k values]]
                   (op/apply [operation-k (apply + values)] base-value))
                 base-value))))

(comment

 (let [->entity (fn [modifiers]
                  (map->Entity {:entity/modifiers modifiers}))]
   (and
    (= (->modified-value (->entity {:modifier/damage-deal {:op/val-inc [30]
                                                           :op/val-mult [0.5]}})
                         :modifier/damage-deal
                         [5 10])
       [52 52])
    (= (->modified-value (->entity {:modifier/damage-deal {:op/val-inc [30]}
                                    :stats/fooz-barz {:op/babu [1 2 3]}})
                         :modifier/damage-deal
                         [5 10])
       [35 35])
    (= (->modified-value (map->Entity {})
                         :modifier/damage-deal
                         [5 10])
       [5 10])
    (= (->modified-value (->entity {:modifier/hp {:op/max-inc [10 1]
                                                  :op/max-mult [0.5]}})
                         :modifier/hp
                         [100 100])
       [100 166])
    (= (->modified-value (->entity {:modifier/movement-speed {:op/inc [2]
                                                              :op/mult [0.1 0.2]}})
                         :modifier/movement-speed
                         3)
       6.5)))
 )

(g/def-markup-color "ITEM_GOLD" [0.84 0.8 0.52])

(defc :property/pretty-name
  {:data :string
   :let value}
  (component/info [_]
    (str "[ITEM_GOLD]"value"[]")))

(property/def :properties/items
  {:schema [:property/pretty-name
            :entity/image
            :item/slot
            [:item/modifiers {:optional true}]]
   :overview {:title "Items"
              :columns 20
              :image/scale 1.1
              :sort-by-fn #(vector (if-let [slot (:item/slot %)]
                                     (name slot)
                                     "")
                             (name (:property/id %)))}})

(def ^:private empty-inventory
  (->> #:inventory.slot{:bag      [6 4]
                        :weapon   [1 1]
                        :shield   [1 1]
                        :helm     [1 1]
                        :chest    [1 1]
                        :leg      [1 1]
                        :glove    [1 1]
                        :boot     [1 1]
                        :cloak    [1 1]
                        :necklace [1 1]
                        :rings    [2 1]}
       (map (fn [[slot [width height]]]
              [slot (g2d/create-grid width height (constantly nil))]))
       (into {})))

(defc :item/modifiers
  {:data [:components-ns :modifier]
   :let modifiers}
  (component/info [_]
    (when (seq modifiers)
      (mod-info-text modifiers))))

(defc :item/slot
  {:data (apply vector :enum (keys empty-inventory))})

(def ^:private body-props
  {:width 0.75
   :height 0.75
   :z-order :z-order/on-ground})

(defc :tx/item
  (do! [[_ position item]]
    [[:e/create position body-props {:entity/image (:entity/image item)
                                     :entity/item item
                                     :entity/clickable {:type :clickable/item
                                                        :text (:property/pretty-name item)}}]]))

(defn- cells-and-items [inventory slot]
  (for [[position item] (slot inventory)]
    [[slot position] item]))

(defn valid-slot? [[slot _] item]
  (or (= :inventory.slot/bag slot)
      (= (:item/slot item) slot)))

(defn- applies-modifiers? [[slot _]]
  (not= :inventory.slot/bag slot))

(defn stackable? [item-a item-b]
  (and (:count item-a)
       (:count item-b) ; this is not required but can be asserted, all of one name should have count if others have count
       (= (:property/id item-a) (:property/id item-b))))

(defn- set-item [{:keys [entity/id] :as entity*} cell item]
  (let [inventory (:entity/inventory entity*)]
    (assert (and (nil? (get-in inventory cell))
                 (valid-slot? cell item))))
  [[:e/assoc-in id (cons :entity/inventory cell) item]
   (when (applies-modifiers? cell)
     [:tx/apply-modifiers id (:item/modifiers item)])
   (when (:entity/player? entity*)
     [:tx/set-item-image-in-widget cell item])])

(defn- remove-item [{:keys [entity/id] :as entity*} cell]
  (let [item (get-in (:entity/inventory entity*) cell)]
    (assert item)
    [[:e/assoc-in id (cons :entity/inventory cell) nil]
     (when (applies-modifiers? cell)
       [:tx/reverse-modifiers id (:item/modifiers item)])
     (when (:entity/player? entity*)
       [:tx/remove-item-from-widget cell])]))

(defc :tx/set-item
  (do! [[_ entity cell item]]
    (set-item @entity cell item)))

(defc :tx/remove-item
  (do! [[_ entity cell]]
    (remove-item @entity cell)))

; TODO doesnt exist, stackable, usable items with action/skillbar thingy
#_(defn remove-one-item [entity cell]
  (let [item (get-in (:entity/inventory @entity) cell)]
    (if (and (:count item)
             (> (:count item) 1))
      (do
       ; TODO this doesnt make sense with modifiers ! (triggered 2 times if available)
       ; first remove and then place, just update directly  item ...
       (remove-item! entity cell)
       (set-item! entity cell (update item :count dec)))
      (remove-item! entity cell))))

; TODO no items which stack are available
(defn- stack-item [entity* cell item]
  (let [cell-item (get-in (:entity/inventory entity*) cell)]
    (assert (stackable? item cell-item))
    ; TODO this doesnt make sense with modifiers ! (triggered 2 times if available)
    ; first remove and then place, just update directly  item ...
    (concat (remove-item entity* cell)
            (set-item entity* cell (update cell-item :count + (:count item))))))

(defc :tx/stack-item
  (do! [[_ entity cell item]]
    (stack-item @entity cell item)))

(defn- try-put-item-in [entity* slot item]
  (let [inventory (:entity/inventory entity*)
        cells-items (cells-and-items inventory slot)
        [cell _cell-item] (find-first (fn [[_cell cell-item]] (stackable? item cell-item))
                                      cells-items)]
    (if cell
      (stack-item entity* cell item)
      (when-let [[empty-cell] (find-first (fn [[_cell item]] (nil? item))
                                          cells-items)]
        (set-item entity* empty-cell item)))))

(defn- pickup-item [entity* item]
  (or
   (try-put-item-in entity* (:item/slot item)   item)
   (try-put-item-in entity* :inventory.slot/bag item)))

(defc :tx/pickup-item
  (do! [[_ entity item]]
    (pickup-item @entity item)))

(extend-type clojure.gdx.Entity
  Inventory
  (can-pickup-item? [entity* item]
    (boolean (pickup-item entity* item))))

(defc :entity/inventory
  {:data [:one-to-many :properties/items]}
  (create [[_ items] eid]
    (cons [:e/assoc eid :entity/inventory empty-inventory]
          (for [item items]
            [:tx/pickup-item eid item]))))


; Items are also smaller than 48x48 all of them
; so wasting space ...
; can maybe make a smaller textureatlas or something...

(def ^:private cell-size 48)
(def ^:private droppable-color    [0   0.6 0 0.8])
(def ^:private not-allowed-color  [0.6 0   0 0.8])

(defn- draw-cell-rect [player-entity* x y mouseover? cell]
  (g/draw-rectangle x y cell-size cell-size :gray)
  (when (and mouseover?
             (= :player-item-on-cursor (entity-state player-entity*)))
    (let [item (:entity/item-on-cursor player-entity*)
          color (if (valid-slot? cell item)
                  droppable-color
                  not-allowed-color)]
      (g/draw-filled-rectangle (inc x) (inc y) (- cell-size 2) (- cell-size 2) color))))

; TODO why do I need to call getX ?
; is not layouted automatically to cell , use 0/0 ??
; (maybe (.setTransform stack true) ? , but docs say it should work anyway
(defn- draw-rect-actor []
  (ui/widget
   (fn [this]
     (draw-cell-rect @world-player
                     (a/x this)
                     (a/y this)
                     (a/mouseover? this (g/gui-mouse-position))
                     (a/id (a/parent this))))))

(defsystem clicked-inventory-cell [_ cell])
(defmethod clicked-inventory-cell :default [_ cell])

(defn- player-clicked-inventory [cell]
  (clicked-inventory-cell (state-obj @world-player) cell))

(defn- ->cell [slot->background slot & {:keys [position]}]
  (let [cell [slot (or position [0 0])]
        image-widget (ui/image-widget (slot->background slot) {:id :image})
        stack (ui/stack [(draw-rect-actor) image-widget])]
    (a/set-name! stack "inventory-cell")
    (a/set-id! stack cell)
    (a/add-listener! stack (proxy [com.badlogic.gdx.scenes.scene2d.utils.ClickListener] []
                             (clicked [event x y]
                               (effect! (player-clicked-inventory cell)))))
    stack))

(defn- slot->background []
  (let [sheet (g/sprite-sheet "images/items.png" 48 48)]
    (->> #:inventory.slot {:weapon   0
                           :shield   1
                           :rings    2
                           :necklace 3
                           :helm     4
                           :cloak    5
                           :chest    6
                           :leg      7
                           :glove    8
                           :boot     9
                           :bag      10} ; transparent
         (map (fn [[slot y]]
                (let [drawable (ui/texture-region-drawable (:texture-region (g/sprite sheet [21 (+ y 2)])))]
                  (ui/set-min-size! drawable cell-size)
                  [slot
                   (ui/tinted-drawable drawable (g/->color 1 1 1 0.4))])))
         (into {}))))

(import 'com.badlogic.gdx.scenes.scene2d.ui.Table)

; TODO move together with empty-inventory definition ?
(defn- redo-table! [^Table table slot->background]
  ; cannot do add-rows, need bag :position idx
  (let [cell (fn [& args] (apply ->cell slot->background args))]
    (.clear table) ; no need as we create new table ... TODO
    (doto table .add .add
      (.add (cell :inventory.slot/helm))
      (.add (cell :inventory.slot/necklace)) .row)
    (doto table .add
      (.add (cell :inventory.slot/weapon))
      (.add (cell :inventory.slot/chest))
      (.add (cell :inventory.slot/cloak))
      (.add (cell :inventory.slot/shield)) .row)
    (doto table .add .add
      (.add (cell :inventory.slot/leg)) .row)
    (doto table .add
      (.add (cell :inventory.slot/glove))
      (.add (cell :inventory.slot/rings :position [0 0]))
      (.add (cell :inventory.slot/rings :position [1 0]))
      (.add (cell :inventory.slot/boot)) .row)
    ; TODO add separator
    (doseq [y (range (g2d/height (:inventory.slot/bag empty-inventory)))]
      (doseq [x (range (g2d/width (:inventory.slot/bag empty-inventory)))]
        (.add table (cell :inventory.slot/bag :position [x y])))
      (.row table))))

(defn ->inventory-window [{:keys [slot->background]}]
  (let [table (ui/table {:id ::table})]
    (redo-table! table slot->background)
    (ui/window {:title "Inventory"
                :id :inventory-window
                :visible? false
                :pack? true
                :position [(g/gui-viewport-width)
                           (g/gui-viewport-height)]
                :rows [[{:actor table :pad 4}]]})))

(defn ->inventory-window-data [] (slot->background))

(declare world-widgets)

(defn- get-inventory []
  {:table (::table (get (:windows (stage-get)) :inventory-window))
   :slot->background (:slot->background world-widgets)})

(defc :tx/set-item-image-in-widget
  (do! [[_ cell item]]
    (let [{:keys [table]} (get-inventory)
          cell-widget (get table cell)
          image-widget (get cell-widget :image)
          drawable (ui/texture-region-drawable (:texture-region (:entity/image item)))]
      (ui/set-min-size! drawable cell-size)
      (ui/set-drawable! image-widget drawable)
      (ui/add-tooltip! cell-widget #(component/info-text item))
      nil)))

(defc :tx/remove-item-from-widget
  (do! [[_ cell]]
    (let [{:keys [table slot->background]} (get-inventory)
          cell-widget (get table cell)
          image-widget (get cell-widget :image)]
      (ui/set-drawable! image-widget (slot->background (cell 0)))
      (ui/remove-tooltip! cell-widget)
      nil)))

(defsystem enter)
(defmethod enter :default [_])

(defsystem exit)
(defmethod exit :default  [_])

(defsystem player-enter)
(defmethod player-enter :default [_])

(defsystem pause-game?)
(defmethod pause-game? :default [_])

(defsystem manual-tick)
(defmethod manual-tick :default [_])

(defc :tx/sound
  {:data :sound}
  (do! [[_ file]]
    (play-sound! file)
    nil))

(defc :tx/cursor
  (do! [[_ cursor-key]]
    (g/set-cursor! cursor-key)
    nil))

; TODO no window movable type cursor appears here like in player idle
; inventory still working, other stuff not, because custom listener to keypresses ? use actor listeners?
; => input events handling
; hmmm interesting ... can disable @ item in cursor  / moving / etc.

(defn- show-player-modal! [{:keys [title text button-text on-click]}]
  (assert (not (::modal (stage-get))))
  (stage-add! (ui/window {:title title
                          :rows [[(ui/label text)]
                                 [(ui/text-button button-text
                                                  (fn []
                                                    (a/remove! (::modal (stage-get)))
                                                    (on-click)))]]
                          :id ::modal
                          :modal? true
                          :center-position [(/ (g/gui-viewport-width) 2)
                                            (* (g/gui-viewport-height) (/ 3 4))]
                          :pack? true})))

(defc :tx/player-modal
  (do! [[_ params]]
    (show-player-modal! params)
    nil))
