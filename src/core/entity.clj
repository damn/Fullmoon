(ns core.entity
  (:require [malli.core :as m]
            [core.math.geom :as geom]
            [core.math.vector :as v]
            [core.utils.core :as utils :refer [find-first]]
            [core.ctx :refer :all]
            [core.graphics.camera :as camera]
            [core.graphics.image :as image]
            [core.property :as property]
            [core.ui :as ui]
            [core.world.raycaster :refer [ray-blocked?]]
            [core.world.grid :as grid]
            [core.world.time :as time]))

(defsystem create "Create entity with eid for txs side-effects. Default nil."
  [_ entity ctx])
(defmethod create :default [_ entity ctx])

(defsystem destroy "FIXME" [_ entity ctx])
(defmethod destroy :default [_ entity ctx])

(defsystem tick "FIXME" [_ entity ctx])
(defmethod tick :default [_ entity ctx])

; java.lang.IllegalArgumentException: No method in multimethod 'render-info' for dispatch value: :position
; actually we dont want this to be called over that
; it should be :components? then ?
; => shouldn't need default fns for render -> don't call it if its not there

; every component has parent-entity-id (peid)
; fetch active entity-ids
; then fetch all components which implement render-below
; and have parent-id in entity-ids, etc.

(defsystem render-below "FIXME" [_ entity* g ctx])
(defmethod render-below :default [_ entity* g ctx])

(defsystem render "FIXME" [_ entity* g ctx])
(defmethod render :default [_ entity* g ctx])

(defsystem render-above "FIXME" [_ entity* g ctx])
(defmethod render-above :default [_ entity* g ctx])

(defsystem render-info "FIXME" [_ entity* g ctx])
(defmethod render-info :default [_ entity* g ctx])

(def render-systems [render-below
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

(def hpbar-height-px 5)

(def z-orders [:z-order/on-ground
               :z-order/ground
               :z-order/flying
               :z-order/effect])

(def render-order (utils/define-order z-orders))

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

(defn ->Body [{[x y] :position
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

(defn tile [entity*]
  (utils/->tile (:position entity*)))

(defn direction [entity* other-entity*]
  (v/direction (:position entity*) (:position other-entity*)))

(defn collides? [entity* other-entity*]
  (geom/collides? entity* other-entity*))

(defprotocol State
  (state [_])
  (state-obj [_]))

(defprotocol Inventory
  (can-pickup-item? [_ item]))

(defprotocol Stats
  (stat [_ stat] "Calculating value of the stat w. modifiers"))

(defprotocol Modifiers
  (->modified-value [_ modifier-k base-value]))

; does not take into account zoom - but zoom is only for debug ???
; vision range?
(defn- on-screen? [entity* ctx]
  (let [[x y] (:position entity*)
        x (float x)
        y (float y)
        [cx cy] (camera/position (world-camera ctx))
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

(defprotocol Animation
  (anim-tick [_ delta])
  (restart [_])
  (stopped? [_])
  (current-frame [_]))

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

  (stopped? [_]
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
  (->animation (map #(image/edn->image % ctx) frames)
               :frame-duration frame-duration
               :looping? looping?))

(defcomponent :data/animation
  {:schema [:map {:closed true}
            [:frames :some]
            [:frame-duration pos?]
            [:looping? :boolean]]})

(defmethod property/edn->value :data/animation [_ animation ctx]
  (edn->animation animation ctx))

; looping? - click on widget restart
; frame-duration
; frames ....
; hidden actor act tick atom animation & set current frame image drawable
(defmethod property/->widget :data/animation [_ animation ctx]
  (ui/->table {:rows [(for [image (:frames animation)]
                        (ui/->image-widget (image/edn->image image ctx) {}))]
               :cell-defaults {:pad 1}}))

(defn- tx-assoc-image-current-frame [eid animation]
  [:e/assoc eid :entity/image (current-frame animation)])

(defcomponent :entity/animation
  {:data :data/animation
   :let animation}
  (create [_ eid _ctx]
    [(tx-assoc-image-current-frame eid animation)])

  (tick [[k _] eid ctx]
    [(tx-assoc-image-current-frame eid animation)
     [:e/assoc eid k (anim-tick animation (time/delta-time ctx))]]))

(defcomponent :entity/delete-after-animation-stopped?
  (create [_ entity _ctx]
    (-> @entity :entity/animation :looping? not assert))

  (tick [_ entity _ctx]
    (when (stopped? (:entity/animation @entity))
      [[:e/destroy entity]])))

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
    (time/->counter ctx duration))

  (info-text [_ ctx]
    (str "[LIGHT_GRAY]Remaining: " (utils/readable-number (time/finished-ratio ctx counter)) "/1[]"))

  (tick [_ eid ctx]
    (when (time/stopped? ctx counter)
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

(defn enemy-faction [{:keys [entity/faction]}]
  (case faction
    :evil :good
    :good :evil))

(defn friendly-faction [{:keys [entity/faction]}]
  faction)

(defcomponent :effect.entity/convert
  {:data :some}
  (info-text [_ _effect-ctx]
    "Converts target to your side.")

  (applicable? [_ {:keys [effect/source effect/target]}]
    (and target
         (= (:entity/faction @target)
            (enemy-faction @source))))

  (do! [_ {:keys [effect/source effect/target]}]
    [[:tx/audiovisual (:position @target) :audiovisuals/convert]
     [:e/assoc target :entity/faction (friendly-faction @source)]]))

(defcomponent :entity/image
  {:data :image
   :let image}
  (render [_ entity* g _ctx]
    (draw-rotated-centered-image g
                                 image
                                 (or (:rotation-angle entity*) 0)
                                 (:position entity*))))

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

(def ^:private outline-alpha 0.4)
(def ^:private enemy-color    [1 0 0 outline-alpha])
(def ^:private friendly-color [0 1 0 outline-alpha])
(def ^:private neutral-color  [1 1 1 outline-alpha])

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

(defn- move-position [position {:keys [direction speed delta-time]}]
  (mapv #(+ %1 (* %2 speed delta-time)) position direction))

(defn- move-body [body movement]
  (-> body
      (update :position    move-position movement)
      (update :left-bottom move-position movement)))

(defn- valid-position? [grid {:keys [entity/id z-order] :as body}]
  {:pre [(:collides? body)]}
  (let [cells* (into [] (map deref) (grid/rectangle->cells grid body))]
    (and (not-any? #(grid/blocked? % z-order) cells*)
         (->> cells*
              grid/cells->entities
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

(defcomponent :entity/movement
  {:let {:keys [direction speed rotate-in-movement-direction?] :as movement}}
  (tick [_ eid ctx]
    (assert (m/validate movement-speed-schema speed))
    (assert (or (zero? (v/length direction))
                (v/normalised? direction)))
    (when-not (or (zero? (v/length direction))
                  (nil? speed)
                  (zero? speed))
      (let [movement (assoc movement :delta-time (time/delta-time ctx))
            body @eid]
        (when-let [body (if (:collides? body) ; < == means this is a movement-type ... which could be a multimethod ....
                          (try-move-solid-body (:context/grid ctx) body movement)
                          (move-body body movement))]
          [[:e/assoc eid :position    (:position    body)]
           [:e/assoc eid :left-bottom (:left-bottom body)]
           (when rotate-in-movement-direction?
             [:e/assoc eid :rotation-angle (v/get-angle-from-vector direction)])
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

; TODO add teleport effect ~ or tx

(defcomponent :entity/projectile-collision
  {:let {:keys [entity-effects already-hit-bodies piercing?]}}
  (->mk [[_ v] _ctx]
    (assoc v :already-hit-bodies #{}))

  ; TODO probably belongs to body
  (tick [[k _] entity ctx]
    ; TODO this could be called from body on collision
    ; for non-solid
    ; means non colliding with other entities
    ; but still collding with other stuff here ? o.o
    (let [entity* @entity
          cells* (map deref (grid/rectangle->cells (:context/grid ctx) entity*)) ; just use cached-touched -cells
          hit-entity (find-first #(and (not (contains? already-hit-bodies %)) ; not filtering out own id
                                       (not= (:entity/faction entity*) ; this is not clear in the componentname & what if they dont have faction - ??
                                             (:entity/faction @%))
                                       (:collides? @%)
                                       (collides? entity* @%))
                                 (grid/cells->entities cells*))
          destroy? (or (and hit-entity (not piercing?))
                       (some #(grid/blocked? % (:z-order entity*)) cells*))
          id (:entity/id entity*)]
      [(when hit-entity
         [:e/assoc-in id [k :already-hit-bodies] (conj already-hit-bodies hit-entity)]) ; this is only necessary in case of not piercing ...
       (when destroy?
         [:e/destroy id])
       (when hit-entity
         [:tx/effect {:effect/source id :effect/target hit-entity} entity-effects])])))

(def ^:private shout-radius 4)

(defn- friendlies-in-radius [ctx position faction]
  (->> {:position position
        :radius shout-radius}
       (grid/circle->entities (:context/grid ctx))
       (map deref)
       (filter #(= (:entity/faction %) faction))
       (map :entity/id)))

(defcomponent :entity/alert-friendlies-after-duration
  {:let {:keys [counter faction]}}
  (tick [_ eid ctx]
    (when (time/stopped? ctx counter)
      (cons [:e/destroy eid]
            (for [friendly-eid (friendlies-in-radius ctx (:position @eid) faction)]
              [:tx/event friendly-eid :alert])))))

(defcomponent :tx/shout
  (do! [[_ position faction delay-seconds] ctx]
    [[:e/create
      position
      effect-body-props
      {:entity/alert-friendlies-after-duration
       {:counter (time/->counter ctx delay-seconds)
        :faction faction}}]]))

(defcomponent :entity/skills
  {:data [:one-to-many :properties/skills]}
  (create [[k skills] eid ctx]
    (cons [:e/assoc eid k nil]
          (for [skill skills]
            [:tx/add-skill eid skill])))

  (info-text [[_ skills] _ctx]
    ; => recursive info-text leads to endless text wall
    #_(when (seq skills)
        (str "[VIOLET]Skills: " (str/join "," (map name (keys skills))) "[]")))

  (tick [[k skills] eid ctx]
    (for [{:keys [skill/cooling-down?] :as skill} (vals skills)
          :when (and cooling-down?
                     (time/stopped? ctx cooling-down?))]
      [:e/assoc-in eid [k (:property/id skill) :skill/cooling-down?] false])))

(defn has-skill? [{:keys [entity/skills]} {:keys [property/id]}]
  (contains? skills id))

(defcomponent :tx/add-skill
  (do! [[_ entity {:keys [property/id] :as skill}] _ctx]
    (assert (not (has-skill? @entity skill)))
    [[:e/assoc-in entity [:entity/skills id] skill]
     (when (:entity/player? @entity)
       [:tx.action-bar/add skill])]))

(defcomponent :tx/remove-skill
  (do! [[_ entity {:keys [property/id] :as skill}] _ctx]
    (assert (has-skill? @entity skill))
    [[:e/dissoc-in entity [:entity/skills id]]
     (when (:entity/player? @entity)
       [:tx.action-bar/remove skill])]))

(defcomponent :entity/string-effect
  (tick [[k {:keys [counter]}] eid context]
    (when (time/stopped? context counter)
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
            (update :counter #(time/reset ctx %)))
        {:text text
         :counter (time/->counter ctx 0.4)})]]))
