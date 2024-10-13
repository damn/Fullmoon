(ns world.entity
  (:require [clj-commons.pretty.repl :refer [pretty-pst]]
            [clojure.gdx.graphics :as g]
            [clojure.gdx.graphics.camera :as 🎥]
            [clojure.gdx.math.shape :as shape]
            [clojure.gdx.math.vector :as v]
            [core.component :refer [defsystem defc do! effect!] :as component]
            [core.db :as db]
            [malli.core :as m]
            [utils.core :refer [define-order sort-by-order ->tile safe-merge readable-number]]
            [world.grid :as grid :refer [world-grid]]
            [world.player :refer [world-player]]
            [world.raycaster :refer [ray-blocked?]]
            [world.time :refer [world-delta ->counter finished-ratio stopped?]]))

(defsystem create [_ entity])
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

(defn tile [entity*]
  (->tile (:position entity*)))

(def ^{:doc "For effects just to have a mouseover body size for debugging purposes."}
  effect-body-props
  {:width 0.5
   :height 0.5
   :z-order :z-order/effect})

(defn direction [entity* other-entity*]
  (v/direction (:position entity*) (:position other-entity*)))

(defn collides? [entity* other-entity*]
  (shape/overlaps? entity* other-entity*))

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

(declare ^:private uids-entities)

(defn init-uids-entities! []
  (.bindRoot #'uids-entities {}))

(defn all-entities []
  (vals uids-entities))

(defn get-entity
  "Mostly used for debugging, use an entity's atom for (probably) faster access in your logic."
  [uid]
  (get uids-entities uid))

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

(defc :entity/id
  (create  [[_ id] _eid] [[:tx/add-to-world      id]])
  (destroy [[_ id] _eid] [[:tx/remove-from-world id]]))

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

(defc :entity/delete-after-duration
  {:let counter}
  (component/create [[_ duration]]
    (->counter duration))

  (component/info [_]
    (str "[LIGHT_GRAY]Remaining: " (readable-number (finished-ratio counter)) "/1[]"))

  (tick [_ eid]
    (when (stopped? counter)
      [[:e/destroy eid]])))

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
