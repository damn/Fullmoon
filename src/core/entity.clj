(ns core.entity
  (:require [clojure.gdx :refer :all]
            [clojure.ctx :refer :all]
            [malli.core :as m]
            [clj-commons.pretty.repl :refer [pretty-pst]]))

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

(defn- define-order [order-k-vector]
  (apply hash-map
         (interleave order-k-vector (range))))

(defn- sort-by-order [coll get-item-order-k order]
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

; java.lang.IllegalArgumentException: No method in multimethod 'render-info' for dispatch value: :position
; actually we dont want this to be called over that
; it should be :components? then ?
; => shouldn't need default fns for render -> don't call it if its not there

; every component has parent-entity-id (peid)
; fetch active entity-ids
; then fetch all components which implement render-below
; and have parent-id in entity-ids, etc.


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

(def ^:private render-order (define-order z-orders))

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

(defn entity-tile [entity*]
  (->tile (:position entity*)))

(defn direction [entity* other-entity*]
  (v-direction (:position entity*) (:position other-entity*)))

(defn collides? [entity* other-entity*]
  (shape-collides? entity* other-entity*))

(defprotocol State
  (entity-state [_])
  (state-obj [_]))

(defprotocol Inventory
  (can-pickup-item? [_ item]))

(defprotocol Stats
  (entity-stat [_ stat] "Calculating value of the stat w. modifiers"))

(defprotocol Modifiers
  (->modified-value [_ modifier-k base-value]))

; does not take into account zoom - but zoom is only for debug ???
; vision range?
(defn- on-screen? [entity* ctx]
  (let [[x y] (:position entity*)
        x (float x)
        y (float y)
        [cx cy] (camera-position (world-camera ctx))
        px (float cx)
        py (float cy)
        xdist (Math/abs (- x px))
        ydist (Math/abs (- y py))]
    (and
     (<= xdist (inc (/ (float (world-viewport-width ctx))  2)))
     (<= ydist (inc (/ (float (world-viewport-height ctx)) 2))))))

; TODO at wrong point , this affects targeting logic of npcs
; move the debug flag to either render or mouseover or lets see
(def ^:private ^:dbg-flag los-checks? true)

; does not take into account size of entity ...
; => assert bodies <1 width then
(defn line-of-sight? [context source* target*]
  (and (or (not (:entity/player? source*))
           (on-screen? target* context))
       (not (and los-checks?
                 (ray-blocked? context (:position source*) (:position target*))))))

(defprotocol Player
  (player-entity [ctx])
  (player-entity* [ctx])
  (player-update-state      [ctx])
  (player-state-pause-game? [ctx])
  (player-clicked-inventory [ctx cell])
  (player-clicked-skillmenu [ctx skill]))

(defsystem create "Create entity with eid for txs side-effects. Default nil."
  [_ entity ctx])
(defmethod create :default [_ entity ctx])

(defsystem destroy "FIXME" [_ entity ctx])
(defmethod destroy :default [_ entity ctx])

(defsystem tick "FIXME" [_ entity ctx])
(defmethod tick :default [_ entity ctx])

(defsystem render-below "FIXME" [_ entity* g ctx])
(defmethod render-below :default [_ entity* g ctx])

(defsystem render "FIXME" [_ entity* g ctx])
(defmethod render :default [_ entity* g ctx])

(defsystem render-above "FIXME" [_ entity* g ctx])
(defmethod render-above :default [_ entity* g ctx])

(defsystem render-info "FIXME" [_ entity* g ctx])
(defmethod render-info :default [_ entity* g ctx])

(def ^:private render-systems [render-below
                               render
                               render-above
                               render-info])

(defcomponent :context/ecs
  (->mk [_ _ctx]
    {}))

(defn- entities [ctx] (:context/ecs ctx)) ; dangerous name!

(let [cnt (atom 0)]
  (defn- unique-number! []
    (swap! cnt inc)))

(defn- create-e-system [eid]
  (for [component @eid]
    (fn [ctx]
      ; we are assuming components dont remove other ones at entity/create
      ; thats why we reuse component and not fetch each time again for key
      (create component eid ctx))))

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

(def ^:private ^:dbg-flag show-body-bounds false)

(defn- draw-body-rect [g entity* color]
  (let [[x y] (:left-bottom entity*)]
    (draw-rectangle g x y (:width entity*) (:height entity*) color)))

(defn- render-entity* [system entity* g ctx]
  (try
   (when show-body-bounds
     (draw-body-rect g entity* (if (:collides? entity*) :white :gray)))
   (run! #(system % entity* g ctx) entity*)
   (catch Throwable t
     (draw-body-rect g entity* :red)
     (pretty-pst t 12))))

(defn- tick-system [ctx entity]
  (try
   (reduce (fn do-tick-component [ctx k]
             ; precaution in case a component gets removed by another component
             ; the question is do we still want to update nil components ?
             ; should be contains? check ?
             ; but then the 'order' is important? in such case dependent components
             ; should be moved together?
             (if-let [v (k @entity)]
               (let [component [k v]]
                 (effect! ctx (tick component entity ctx)))
               ctx))
           ctx
           (keys @entity))
   (catch Throwable t
     (throw (ex-info "" (select-keys @entity [:entity/uid]) t))
     ctx)))

(defn all-entities [ctx]
  (vals (entities ctx)))

(defn get-entity
  "Mostly used for debugging, use an entity's atom for (probably) faster access in your logic."
  [ctx uid]
  (get (entities ctx) uid))

(defn tick-entities!
  "Calls tick system on all components of entities."
  [ctx entities]
  (reduce tick-system ctx entities))

(defn render-entities!
  "Draws entities* in the correct z-order and in the order of render-systems for each z-order."
  [ctx g entities*]
  (let [player-entity* (player-entity* ctx)]
    (doseq [[z-order entities*] (sort-by-order (group-by :z-order entities*)
                                               first
                                               render-order)
            system render-systems
            entity* entities*
            :when (or (= z-order :z-order/effect)
                      (line-of-sight? ctx player-entity* entity*))]
      (render-entity* system entity* g ctx))))

(defn remove-destroyed-entities!
  "Calls destroy on all entities which are marked with ':e/destroy'"
  [ctx]
  (for [entity (filter (comp :entity/destroyed? deref) (all-entities ctx))
        component @entity]
    (fn [ctx]
      (destroy component entity ctx))))

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

(defn- edn->animation [{:keys [frames frame-duration looping?]} ctx]
  (->animation (map #(edn->image % ctx) frames)
               :frame-duration frame-duration
               :looping? looping?))


(defmethod edn->value :data/animation [_ animation ctx]
  (edn->animation animation ctx))

; looping? - click on widget restart
; frame-duration
; frames ....
; hidden actor act tick atom animation & set current frame image drawable
(defmethod ->widget :data/animation [_ animation ctx]
  (->table {:rows [(for [image (:frames animation)]
                        (->image-widget (edn->image image ctx) {}))]
               :cell-defaults {:pad 1}}))

(defn- tx-assoc-image-current-frame [eid animation]
  [:e/assoc eid :entity/image (current-frame animation)])

(defn enemy-faction [{:keys [entity/faction]}]
  (case faction
    :evil :good
    :good :evil))

(defn friendly-faction [{:keys [entity/faction]}]
  faction)

(def ^:private outline-alpha 0.4)
(def ^:private enemy-color    [1 0 0 outline-alpha])
(def ^:private friendly-color [0 1 0 outline-alpha])
(def ^:private neutral-color  [1 1 1 outline-alpha])

(defn- move-position [position {:keys [direction speed delta-time]}]
  (mapv #(+ %1 (* %2 speed delta-time)) position direction))

(defn- move-body [body movement]
  (-> body
      (update :position    move-position movement)
      (update :left-bottom move-position movement)))

(defn- valid-position? [grid {:keys [entity/id z-order] :as body}]
  {:pre [(:collides? body)]}
  (let [cells* (into [] (map deref) (rectangle->cells grid body))]
    (and (not-any? #(blocked? % z-order) cells*)
         (->> cells*
              cells->entities
              (not-any? (fn [other-entity]
                          (let [other-entity* @other-entity]
                            (and (not= (:entity/id other-entity*) id)
                                 (:collides? other-entity*)
                                 (collides? other-entity* body)))))))))

(defn- try-move [grid body movement]
  (let [new-body (move-body body movement)]
    (when (valid-position? grid new-body)
      new-body)))

; TODO sliding threshold
; TODO name - with-sliding? 'on'
; TODO if direction was [-1 0] and invalid-position then this algorithm tried to move with
; direection [0 0] which is a waste of processor power...
(defn- try-move-solid-body [grid body {[vx vy] :direction :as movement}]
  (let [xdir (Math/signum (float vx))
        ydir (Math/signum (float vy))]
    (or (try-move grid body movement)
        (try-move grid body (assoc movement :direction [xdir 0]))
        (try-move grid body (assoc movement :direction [0 ydir])))))

; TODO add teleport effect ~ or tx

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

(def ^:private ctx-mouseover-entity :context/mouseover-entity)

(defn mouseover-entity* [ctx]
  (when-let [entity (ctx-mouseover-entity ctx)]
    @entity))

(defn update-mouseover-entity [ctx]
  (let [entity (if (mouse-on-actor? ctx)
                 nil
                 (calculate-mouseover-entity ctx))]
    [(when-let [old-entity (ctx-mouseover-entity ctx)]
       [:e/dissoc old-entity :entity/mouseover?])
     (when entity
       [:e/assoc entity :entity/mouseover? true])
     (fn [ctx]
       (assoc ctx ctx-mouseover-entity entity))]))

;;;; Entity Components

(defcomponent :entity/id
  (create  [[_ id] _eid _ctx] [[:tx/add-to-world      id]])
  (destroy [[_ id] _eid _ctx] [[:tx/remove-from-world id]]))

(defcomponent :entity/uid
  {:let uid}
  (create [_ entity ctx]
    (assert (number? uid))
    (update ctx :context/ecs assoc uid entity))

  (destroy [_ _entity ctx]
    (assert (contains? (entities ctx) uid))
    (update ctx :context/ecs dissoc uid)))

(defcomponent :entity/movement
  {:let {:keys [direction speed rotate-in-movement-direction?] :as movement}}
  (tick [_ eid ctx]
    (assert (m/validate movement-speed-schema speed))
    (assert (or (zero? (v-length direction))
                (v-normalised? direction)))
    (when-not (or (zero? (v-length direction))
                  (nil? speed)
                  (zero? speed))
      (let [movement (assoc movement :delta-time (world-delta ctx))
            body @eid]
        (when-let [body (if (:collides? body) ; < == means this is a movement-type ... which could be a multimethod ....
                          (try-move-solid-body (:context/grid ctx) body movement)
                          (move-body body movement))]
          [[:e/assoc eid :position    (:position    body)]
           [:e/assoc eid :left-bottom (:left-bottom body)]
           (when rotate-in-movement-direction?
             [:e/assoc eid :rotation-angle (v-get-angle-from-vector direction)])
           [:tx/position-changed eid]])))))

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

(defcomponent :entity/image
  {:data :image
   :let image}
  (render [_ entity* g _ctx]
    (draw-rotated-centered-image g
                                 image
                                 (or (:rotation-angle entity*) 0)
                                 (:position entity*))))

(defcomponent :entity/animation
  {:data :data/animation
   :let animation}
  (create [_ eid _ctx]
    [(tx-assoc-image-current-frame eid animation)])

  (tick [[k _] eid ctx]
    [(tx-assoc-image-current-frame eid animation)
     [:e/assoc eid k (anim-tick animation (world-delta ctx))]]))

(defcomponent :entity/clickable
  (render [[_ {:keys [text]}]
           {:keys [entity/mouseover?] :as entity*}
           g
           _ctx]
    (when (and mouseover? text)
      (let [[x y] (:position entity*)]
        (draw-text g
                   {:text text
                    :x x
                    :y (+ y (:half-height entity*))
                    :up? true})))))

(defcomponent :entity/delete-after-duration
  {:let counter}
  (->mk [[_ duration] ctx]
    (->counter ctx duration))

  (info-text [_ ctx]
    (str "[LIGHT_GRAY]Remaining: " (readable-number (finished-ratio ctx counter)) "/1[]"))

  (tick [_ eid ctx]
    (when (stopped? ctx counter)
      [[:e/destroy eid]])))

(defcomponent :entity/destroy-audiovisual
  {:let audiovisuals-id}
  (destroy [_ entity ctx]
    [[:tx/audiovisual (:position @entity) audiovisuals-id]]))

(defcomponent :entity/faction
  {:let faction
   :data [:enum [:good :evil]]}
  (info-text [_ _ctx]
    (str "[SLATE]Faction: " (name faction) "[]")))

(defcomponent :entity/delete-after-animation-stopped?
  (create [_ entity _ctx]
    (-> @entity :entity/animation :looping? not assert))

  (tick [_ entity _ctx]
    (when (anim-stopped? (:entity/animation @entity))
      [[:e/destroy entity]])))

(defcomponent :entity/line-render
  {:let {:keys [thick? end color]}}
  (render [_ entity* g _ctx]
    (let [position (:position entity*)]
      (if thick?
        (with-shape-line-width g 4 #(draw-line g position end color))
        (draw-line g position end color)))))

(defcomponent :tx/line-render
  (do! [[_ {:keys [start end duration color thick?]}] _ctx]
    [[:e/create
      start
      effect-body-props
      #:entity {:line-render {:thick? thick? :end end :color color}
                :delete-after-duration duration}]]))

(defcomponent :entity/mouseover?
  (render-below [_ {:keys [entity/faction] :as entity*} g ctx]
    (let [player-entity* (player-entity* ctx)]
      (with-shape-line-width g 3
        #(draw-ellipse g
                       (:position entity*)
                       (:half-width entity*)
                       (:half-height entity*)
                       (cond (= faction (enemy-faction player-entity*))
                             enemy-color
                             (= faction (friendly-faction player-entity*))
                             friendly-color
                             :else
                             neutral-color))))))

(defcomponent :entity/temp-modifier
  {:let {:keys [counter modifiers]}}
  (info-text [_ ctx]
    (str "[LIGHT_GRAY]Spiderweb - remaining: " (readable-number (finished-ratio ctx counter)) "/1[]"))

  (tick [[k _] eid ctx]
    (when (stopped? ctx counter)
      [[:e/dissoc eid k]
       [:tx/reverse-modifiers eid modifiers]]))

  (render-above [_ entity* g _ctx]
    (draw-filled-circle g (:position entity*) 0.5 [0.5 0.5 0.5 0.4])))

(def ^:private shout-radius 4)

(defn- friendlies-in-radius [ctx position faction]
  (->> {:position position
        :radius shout-radius}
       (circle->entities (:context/grid ctx))
       (map deref)
       (filter #(= (:entity/faction %) faction))
       (map :entity/id)))

(defcomponent :entity/alert-friendlies-after-duration
  {:let {:keys [counter faction]}}
  (tick [_ eid ctx]
    (when (stopped? ctx counter)
      (cons [:e/destroy eid]
            (for [friendly-eid (friendlies-in-radius ctx (:position @eid) faction)]
              [:tx/event friendly-eid :alert])))))

(defcomponent :tx/shout
  (do! [[_ position faction delay-seconds] ctx]
    [[:e/create
      position
      effect-body-props
      {:entity/alert-friendlies-after-duration
       {:counter (->counter ctx delay-seconds)
        :faction faction}}]]))

(defcomponent :entity/string-effect
  (tick [[k {:keys [counter]}] eid ctx]
    (when (stopped? ctx counter)
      [[:e/dissoc eid k]]))

  (render-above [[_ {:keys [text]}] entity* g _ctx]
    (let [[x y] (:position entity*)]
      (draw-text g
                 {:text text
                  :x x
                  :y (+ y (:half-height entity*) (pixels->world-units g hpbar-height-px))
                  :scale 2
                  :up? true}))))

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

(defsystem enter "FIXME" [_ ctx])
(defmethod enter :default [_ ctx])

(defsystem exit  "FIXME" [_ ctx])
(defmethod exit :default  [_ ctx])

(defsystem player-enter "FIXME" [_])
(defmethod player-enter :default [_])

(defsystem pause-game? "FIXME" [_])
(defmethod pause-game? :default [_])

(defsystem manual-tick "FIXME" [_ ctx])
(defmethod manual-tick :default [_ ctx])

(defsystem clicked-inventory-cell "FIXME" [_ cell])
(defmethod clicked-inventory-cell :default [_ cell])

(defsystem clicked-skillmenu-skill "FIXME" [_ skill])
(defmethod clicked-skillmenu-skill :default [_ skill])
