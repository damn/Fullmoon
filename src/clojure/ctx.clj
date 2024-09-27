(ns clojure.ctx
  "## Glossary

  | Name             | Meaning                                        |
  | ---------------  | -----------------------------------------------|
  | `component`      | vector `[k v]` |
  | `system`         | multimethod dispatching on component k |
  | `eid` , `entity` | entity atom                                    |
  | `entity*`        | entity value (defrecord `clojure.ctx.Entity`), |
  | `actor`          | A UI actor, not immutable `com.badlogic.gdx.scenes.scene2d.Actor`        |
  | `cell`/`cell*`   | Cell of the world grid or inventory  |
  | `camera`         | `com.badlogic.gdx.graphics.Camera`             |
  | `g`              | `clojure.ctx.Graphics`                        |
  | `grid`           | `data.grid2d.Grid`                             |
  | `image`          | `clojure.ctx.Image`                          |
  | `position`       | `[x y]` vector                                 |"
  {:metadoc/categories {:app "🖥️ Application"
                        :camera "🎥 Camera"
                        :color "🎨 Color"
                        :component "⚙️ Component"
                        :component-systems "🌀 Component Systems"
                        :drawing "🖌️ Drawing"
                        :entity "👾 Entity"
                        :geometry "📐 Geometry"
                        :image "📸 Image"
                        :input "🎮 Input"
                        :properties "📦 Properties"
                        :time "⏳ Time"
                        :ui "🎛️ UI"
                        :utils "🔧 Utils"
                        :world "🌎 World"}}
  (:require (clojure [set :as set]
                     [string :as str]
                     [edn :as edn]
                     [math :as math]
                     [pprint :refer [pprint]])
            [clj-commons.pretty.repl :refer [pretty-pst]]
            (malli [core :as m]
                   [error :as me]
                   [generator :as mg]))
  (:import java.util.Random
           org.lwjgl.system.Configuration
           (com.badlogic.gdx Gdx ApplicationAdapter Input$Keys)
           com.badlogic.gdx.audio.Sound
           com.badlogic.gdx.assets.AssetManager
           (com.badlogic.gdx.backends.lwjgl3 Lwjgl3Application Lwjgl3ApplicationConfiguration)
           com.badlogic.gdx.files.FileHandle
           [com.badlogic.gdx.math MathUtils Vector2 Vector3 Circle Rectangle Intersector]
           (com.badlogic.gdx.graphics Color Texture Texture$TextureFilter Pixmap Pixmap$Format OrthographicCamera Camera)
           (com.badlogic.gdx.graphics.g2d TextureRegion Batch SpriteBatch BitmapFont)
           [com.badlogic.gdx.graphics.g2d.freetype FreeTypeFontGenerator FreeTypeFontGenerator$FreeTypeFontParameter]
           (com.badlogic.gdx.utils Align Scaling Disposable ScreenUtils SharedLibraryLoader)
           (com.badlogic.gdx.utils.viewport Viewport FitViewport)
           (com.badlogic.gdx.scenes.scene2d Actor Touchable Group Stage)
           (com.badlogic.gdx.scenes.scene2d.ui Label Button Table Cell WidgetGroup Stack ButtonGroup HorizontalGroup VerticalGroup Window)
           (com.badlogic.gdx.scenes.scene2d.utils ChangeListener TextureRegionDrawable Drawable)
           (com.kotcrab.vis.ui VisUI VisUI$SkinScale)
           (com.kotcrab.vis.ui.widget Tooltip VisTextButton VisCheckBox VisSelectBox VisImage VisImageButton VisTextField VisWindow VisTable VisLabel VisSplitPane VisScrollPane Separator)
           space.earlygrey.shapedrawer.ShapeDrawer
           gdl.RayCaster)
  (:load "ctx/utils"
         "ctx/component"
         "ctx/systems"
         "ctx/effect"
         "ctx/assets"
         "ctx/info"
         "ctx/graphics"
         "ctx/time"
         "ctx/world"
         "ctx/geometry"
         "ctx/val_max"
         "ctx/camera"
         "ctx/screens"
         "ctx/ui"
         "ctx/raycaster"
         "ctx/properties"
         ))

(def ^:private add-metadoc? true)

(defn- add-metadoc! []
  (doseq [[doc-cat syms] (edn/read-string (slurp "doc_categories.edn"))
          sym syms]
    (alter-meta! (resolve sym) assoc :metadoc/categories #{doc-cat}))

  (doseq [[sym avar] (ns-publics *ns*)
          :when (::system? (meta avar))]
    (alter-meta! (resolve sym) assoc :metadoc/categories #{:component-systems})))

(defn- anony-class? [[sym avar]]
  (instance? java.lang.Class @avar))

(defn- record-constructor? [[sym avar]]
  (re-find #"(map)?->\p{Upper}" (name sym)))

; TODO only funcs, no macros
; what about record constructors, refer-all -> need to make either private or
; also highlight them ....
; only for categorization not necessary
(defn vimstuff []
  (spit "vimstuff"
        (apply str
               (remove #{"defcomponent" "defsystem"}
                       (interpose " , " (map str (keys (->> (ns-publics *ns*)
                                                            (remove anony-class?)))))))))

(defn- relevant-ns-publics []
  (->> (ns-publics *ns*)
       (remove anony-class?)
       (remove record-constructor?)))
; 1. macros separate
; 2. defsystems separate
; 3. 'v-'
; 4. protocols ?1 protocol functions included ?!

#_(spit "testo"
      (str/join "\n"
                (for [[asym avar] (sort-by first (relevant-ns-publics))]
                  (str asym " " (:arglists (meta avar)))
                  )
                )
      )

       (spit "foooo"(str/join "\n" (sort (map first (relevant-ns-publics)))))
; = 264 public vars
; next remove ->Foo and map->Foo

#_(let [[asym avar] (first (relevant-ns-publics))]
  (str asym " "(:arglists (meta avar)))
  )

;;;; 🎮 libgdx

(declare ^{:tag com.badlogic.gdx.Graphics} gdx-graphics)

(defn- bind-gdx-statics! []
  (.bindRoot #'gdx-graphics Gdx/graphics))

(defn- ->gdx-field [klass-str k]
  (eval (symbol (str "com.badlogic.gdx." klass-str "/" (str/replace (str/upper-case (name k)) "-" "_")))))

(def ^:private ->gdx-input-button (partial ->gdx-field "Input$Buttons"))
(def ^:private ->gdx-input-key    (partial ->gdx-field "Input$Keys"))

(comment
 (and (= (->gdx-input-button :left) 0)
      (= (->gdx-input-button :forward) 4)
      (= (->gdx-input-key :shift-left) 59))
 )

; TODO missing button-pressed?
; also not explaining just-pressed or pressed docs ...
; always link the java class (for all stuff?)
; https://javadoc.io/static/com.badlogicgames.gdx/gdx/1.12.1/com/badlogic/gdx/Input.html#isButtonPressed(int)

(defn button-just-pressed?
  ":left, :right, :middle, :back or :forward."
  [b]
  (.isButtonJustPressed Gdx/input (->gdx-input-button b)))

(defn key-just-pressed?
  "See [[key-pressed?]]."
  [k]
  (.isKeyJustPressed Gdx/input (->gdx-input-key k)))

(defn key-pressed?
  "For options see [libgdx Input$Keys docs](https://javadoc.io/doc/com.badlogicgames.gdx/gdx/latest/com/badlogic/gdx/Input.Keys.html).
  Keys are upper-cased and dashes replaced by underscores.
  For example Input$Keys/ALT_LEFT can be used with :alt-left.
  Numbers via :num-3, etc."
  [k]
  (.isKeyPressed Gdx/input (->gdx-input-key k)))

(defn dispose [obj]
  (Disposable/.dispose obj))

(defprotocol ActiveEntities
  (active-entities [_]))

(com.badlogic.gdx.graphics.Colors/put
 "ITEM_GOLD"
 (com.badlogic.gdx.graphics.Color. (float 0.84)
                                   (float 0.8)
                                   (float 0.52)
                                   (float 1)))

(defcomponent :property/pretty-name
  {:data :string
   :let value}
  (info-text [_ _ctx]
    (str "[ITEM_GOLD]"value"[]")))

(defprotocol PRayCaster
  (ray-blocked? [ctx start target])
  (path-blocked? [ctx start target path-w] "path-w in tiles. casts two rays."))

(defn create-vs
  "Creates a map for every component with map entries `[k (->mk [k v] ctx)]`."
  [components ctx]
  (reduce (fn [m [k v]]
            (assoc m k (->mk [k v] ctx)))
          {}
          components))

(defprotocol Pathfinding
  (potential-fields-follow-to-enemy [ctx eid]))

(defprotocol DrawItemOnCursor
  (draw-item-on-cursor [g ctx]))

(defprotocol WorldGen
  (->world [ctx world-id]))

(def ^:private ctx-msg-player :context/msg-to-player)

(def ^:private duration-seconds 1.5)

(defn- draw-player-message [g ctx]
  (when-let [{:keys [message]} (ctx-msg-player ctx)]
    (draw-text g {:x (/ (gui-viewport-width ctx) 2)
                  :y (+ (/ (gui-viewport-height ctx) 2) 200)
                  :text message
                  :scale 2.5
                  :up? true})))

(defn- check-remove-message [ctx]
  (when-let [{:keys [counter]} (ctx-msg-player ctx)]
    (swap! app-state update ctx-msg-player update :counter + (.getDeltaTime gdx-graphics))
    (when (>= counter duration-seconds)
      (swap! app-state assoc ctx-msg-player nil))))

(defcomponent :widgets/player-message
  (->mk [_ _ctx]
    (->actor {:draw draw-player-message
              :act check-remove-message})))

;;;;️ 👾️ Entities

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

(def ^{:doc "For effects just to have a mouseover body size for debugging purposes."
       :private true}
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

(def ^:private context-ecs :context/ecs)

(defn- entities [ctx] (context-ecs ctx)) ; dangerous name!

(let [cnt (atom 0)]
  (defn- unique-number! []
    (swap! cnt inc)))

(defn- create-e-system [eid]
  (for [component @eid]
    (fn [ctx]
      ; we are assuming components dont remove other ones at entity/create
      ; thats why we reuse component and not fetch each time again for key
      (create component eid ctx))))

(def ^:private ^:dbg-flag show-body-bounds false)

(defn- draw-body-rect [g entity* color]
  (let [[x y] (:left-bottom entity*)]
    (draw-rectangle g x y (:width entity*) (:height entity*) color)))

(defn- render-entity* [system entity* g ctx]
  (try
   (when show-body-bounds
     (draw-body-rect g entity* (if (:collides? entity*) Color/WHITE Color/GRAY)))
   (run! #(system % entity* g ctx) entity*)
   (catch Throwable t
     (draw-body-rect g entity* Color/RED)
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

(def ^:private shout-radius 4)

(defn- friendlies-in-radius [ctx position faction]
  (->> {:position position
        :radius shout-radius}
       (circle->entities (:context/grid ctx))
       (map deref)
       (filter #(= (:entity/faction %) faction))
       (map :entity/id)))

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
    (update ctx context-ecs assoc uid entity))

  (destroy [_ _entity ctx]
    (assert (contains? (entities ctx) uid))
    (update ctx context-ecs dissoc uid)))

(defcomponent :entity/movement
  {:let {:keys [direction speed rotate-in-movement-direction?] :as movement}}
  (tick [_ eid ctx]
    (assert (m/validate movement-speed-schema speed))
    (assert (or (zero? (v-length direction))
                (v-normalised? direction)))
    (when-not (or (zero? (v-length direction))
                  (nil? speed)
                  (zero? speed))
      (let [movement (assoc movement :delta-time (delta-time ctx))
            body @eid]
        (when-let [body (if (:collides? body) ; < == means this is a movement-type ... which could be a multimethod ....
                          (try-move-solid-body (:context/grid ctx) body movement)
                          (move-body body movement))]
          [[:e/assoc eid :position    (:position    body)]
           [:e/assoc eid :left-bottom (:left-bottom body)]
           (when rotate-in-movement-direction?
             [:e/assoc eid :rotation-angle (v-get-angle-from-vector direction)])
           [:tx/position-changed eid]])))))

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
     [:e/assoc eid k (anim-tick animation (delta-time ctx))]]))

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

  (render-above [_ entity* g ctx]
    (draw-filled-circle g (:position entity*) 0.5 [0.5 0.5 0.5 0.4])))

(defcomponent :entity/alert-friendlies-after-duration
  {:let {:keys [counter faction]}}
  (tick [_ eid ctx]
    (when (stopped? ctx counter)
      (cons [:e/destroy eid]
            (for [friendly-eid (friendlies-in-radius ctx (:position @eid) faction)]
              [:tx/event friendly-eid :alert])))))

(defcomponent :entity/string-effect
  (tick [[k {:keys [counter]}] eid context]
    (when (stopped? context counter)
      [[:e/dissoc eid k]]))

  (render-above [[_ {:keys [text]}] entity* g _ctx]
    (let [[x y] (:position entity*)]
      (draw-text g
                 {:text text
                  :x x
                  :y (+ y (:half-height entity*) (pixels->world-units g hpbar-height-px))
                  :scale 2
                  :up? true}))))
;;;;️ Operations

(defn- +? [n]
  (case (math/signum n)
    0.0 ""
    1.0 "+"
    -1.0 ""))

(defn op-info-text [{value 1 :as operation}]
  (str (+? value) (op-value-text operation)))

(defcomponent :op/inc
  {:data :number
   :let value}
  (op-value-text [_] (str value))
  (op-apply [_ base-value] (+ base-value value))
  (op-order [_] 0))

(defcomponent :op/mult
  {:data :number
   :let value}
  (op-value-text [_] (str (int (* 100 value)) "%"))
  (op-apply [_ base-value] (* base-value (inc value)))
  (op-order [_] 1))

(defn- ->pos-int [v]
  (-> v int (max 0)))

(defn- val-max-op-k->parts [op-k]
  (let [[val-or-max inc-or-mult] (mapv keyword (str/split (name op-k) #"-"))]
    [val-or-max (keyword "op" (name inc-or-mult))]))

(comment
 (= (val-max-op-k->parts :op/val-inc) [:val :op/inc])
 )

(defcomponent :op/val-max
  (op-value-text [[op-k value]]
    (let [[val-or-max op-k] (val-max-op-k->parts op-k)]
      (str (op-value-text [op-k value]) " " (case val-or-max
                                              :val "Minimum"
                                              :max "Maximum"))))


  (op-apply [[operation-k value] val-max]
    (assert (m/validate val-max-schema val-max) (pr-str val-max))
    (let [[val-or-max op-k] (val-max-op-k->parts operation-k)
          f #(op-apply [op-k value] %)
          [v mx] (update val-max (case val-or-max :val 0 :max 1) f)
          v  (->pos-int v)
          mx (->pos-int mx)
          vmx (case val-or-max
                :val [v (max v mx)]
                :max [(min v mx) mx])]
      (assert (m/validate val-max-schema vmx))
      vmx))

  (op-order [[op-k value]]
    (let [[_ op-k] (val-max-op-k->parts op-k)]
      (op-order [op-k value]))))

(defcomponent :op/val-inc {:data :int})
(derive       :op/val-inc :op/val-max)

(defcomponent :op/val-mult {:data :number})
(derive       :op/val-mult :op/val-max)

(defcomponent :op/max-inc {:data :int})
(derive       :op/max-inc :op/val-max)

(defcomponent :op/max-mult {:data :number})
(derive       :op/max-mult :op/val-max)

(comment
 (and
  (= (op-apply [:op/val-inc 30]    [5 10]) [35 35])
  (= (op-apply [:op/max-mult -0.5] [5 10]) [5 5])
  (= (op-apply [:op/val-mult 2]    [5 10]) [15 15])
  (= (op-apply [:op/val-mult 1.3]  [5 10]) [11 11])
  (= (op-apply [:op/max-mult -0.8] [5 10]) [1 1])
  (= (op-apply [:op/max-mult -0.9] [5 10]) [0 0]))
 )

;;;;️ Application

(defmacro post-runnable! [& exprs]
  `(.postRunnable Gdx/app (fn [] ~@exprs)))

(defn exit-app! []
  (.exit Gdx/app))

(defn- set-first-screen [context]
  (->> context
       :context/screens
       :first-screen
       (change-screen context)))

(defn create-into
  "For every component `[k v]`  `(->mk [k v] ctx)` is non-nil
  or false, assoc's at ctx k v"
  [ctx components]
  (assert (map? ctx))
  (reduce (fn [ctx [k v]]
            (if-let [v (->mk [k v] ctx)]
              (assoc ctx k v)
              ctx))
          ctx
          components))

(defn- ->application-listener [context]
  (proxy [ApplicationAdapter] []
    (create []
      (bind-gdx-statics!)
      (->> context
           ; screens require vis-ui / properties (map-editor, property editor uses properties)
           (sort-by (fn [[k _]] (if (= k :context/screens) 1 0)))
           (create-into context)
           set-first-screen
           (reset! app-state)))

    (dispose []
      (run! destroy! @app-state))

    (render []
      (ScreenUtils/clear Color/BLACK)
      (screen-render! (current-screen @app-state)))

    (resize [w h]
      ; TODO fix mac screen resize bug again
      (on-resize @app-state w h))))

(defn- ->lwjgl3-app-config [{:keys [title width height full-screen? fps]}]
  ; can remove :pre, we are having a schema now
  ; move schema here too ?
  {:pre [title
         width
         height
         (boolean? full-screen?)
         (or (nil? fps) (int? fps))]}
  ; https://github.com/libgdx/libgdx/pull/7361
  ; Maybe can delete this when using that new libgdx version
  ; which includes this PR.
  (when SharedLibraryLoader/isMac
    (.set Configuration/GLFW_LIBRARY_NAME "glfw_async")
    (.set Configuration/GLFW_CHECK_THREAD0 false))
  (let [config (doto (Lwjgl3ApplicationConfiguration.)
                 (.setTitle title)
                 (.setForegroundFPS (or fps 60)))]
    (if full-screen?
      (.setFullscreenMode config (Lwjgl3ApplicationConfiguration/getDisplayMode))
      (.setWindowedMode config width height))
    ; See https://libgdx.com/wiki/graphics/querying-and-configuring-graphics
    ; but makes no difference
    #_com.badlogic.gdx.graphics.glutils.HdpiMode
    #_(.setHdpiMode config #_HdpiMode/Pixels HdpiMode/Logical)
    config))

(defrecord Context [])

(defn start-app!
  "Validates all properties, then creates the context record and starts a libgdx application with the desktop (lwjgl3) backend.
Sets [[app-state]] atom to the context."
  [properties-edn-file]
  (let [ctx (map->Context (->ctx-properties properties-edn-file))
        app (build-property ctx :app/core)]
    (Lwjgl3Application. (->application-listener (safe-merge ctx (:app/context app)))
                        (->lwjgl3-app-config (:app/lwjgl3 app)))))

;;;; def-attributes

(defn def-attributes [& attributes-data]
  {:pre [(even? (count attributes-data))]}
  (doseq [[k data] (partition 2 attributes-data)]
    (defcomponent* k {:data data})))

(def-attributes
  :tag [:enum [:dev :prod]]
  :configs :some)

(def-attributes
  :views [:map [:gui-view :world-view]]
  :gui-view [:map [:world-width :world-height]]
  :world-view [:map [:tile-size :world-width :world-height]]
  :world-width :pos-int
  :world-height :pos-int
  :tile-size :pos-int
  :default-font [:map [:file :quality-scaling :size]]
  :file :string
  :quality-scaling :pos-int
  :size :pos-int
  :cursors :some)

(def-attributes
  :fps          :nat-int
  :full-screen? :boolean
  :width        :nat-int
  :height       :nat-int
  :title        :string
  :app/lwjgl3 [:map [:fps
                     :full-screen?
                     :width
                     :height
                     :title]]
  :app/context [:map [ctx-assets
                      :context/config
                      :context/graphics
                      :context/screens
                      :context/vis-ui
                      :context/tiled-map-renderer]])

(def-attributes
  :body/width   :pos
  :body/height  :pos
  :body/flying? :boolean)

;;;; def-type

(defn def-type [k {:keys [schema overview]}]
  (defcomponent k
    {:data [:map (conj schema :property/id)]
     :overview overview}))

(def-type :properties/app
  {:schema [:app/lwjgl3
            :app/context]
   :overview {:title "Apps" ; - only 1 ? - no overview - ?
              :columns 10}})

(def-type :properties/audiovisuals
  {:schema [:tx/sound
            :entity/animation]
   :overview {:title "Audiovisuals"
              :columns 10
              :image/scale 2}})

(def-type :properties/items
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

(def-type :properties/creatures
  {:schema [:entity/body
            :property/pretty-name
            :creature/species
            :creature/level
            :entity/animation
            :entity/stats
            :entity/skills
            [:entity/modifiers {:optional true}]
            [:entity/inventory {:optional true}]]
   :overview {:title "Creatures"
              :columns 15
              :image/scale 1.5
              :sort-by-fn #(vector (:creature/level %)
                                   (name (:creature/species %))
                                   (name (:property/id %)))
              :extra-info-text #(str (:creature/level %))}})

(def-type :properties/projectiles
  {:schema [:entity/image
            :projectile/max-range
            :projectile/speed
            :projectile/piercing?
            :entity-effects]
   :overview {:title "Projectiles"
              :columns 16
              :image/scale 2}})

(def-type :properties/skills
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

(def-type :properties/worlds
  {:schema [:world/generator
            :world/player-creature
            [:world/tiled-map {:optional true}]
            [:world/map-size {:optional true}]
            [:world/max-area-level {:optional true}]
            [:world/spawn-rate {:optional true}]]
   :overview {:title "Worlds"
              :columns 10}})


;;;; Transactions

; => every tx calls a function (anyway move to the code logic, keep wiring separate ... ?)
; can make it a one-liner
; or even def-txs?
; or even mark the function with a metadata
; omg
; then components will disappear too ....???
; but then multimethod redeffing doesnt work -> can use vars in development ?

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

(defcomponent :tx/audiovisual
  (do! [[_ position id] ctx]
    (let [{:keys [tx/sound
                  entity/animation]} (build-property ctx id)]
      [[:tx/sound sound]
       [:e/create
        position
        effect-body-props
        {:entity/animation animation
         :entity/delete-after-animation-stopped? true}]])))

(defcomponent :tx/msg-to-player
  (do! [[_ message] ctx]
    (assoc ctx ctx-msg-player {:message message :counter 0})))

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


;;;; Context Components

(defcomponent :context/screens
  {:data :some
   :let screen-ks}
  (->mk [_ ctx]
    {:screens (create-vs (zipmap screen-ks (repeat nil)) ctx)
     :first-screen (first screen-ks)})

  (destroy! [_]
    ; TODO screens not disposed https://github.com/damn/core/issues/41
    ))

(defcomponent :context/config
  {:data [:map [:tag :configs]]
   :let {:keys [tag configs]}}
  (->mk [_ _ctx]
    (get configs tag)))

(defcomponent ctx-assets
  {:data :some
   :let {:keys [folder
                sound-file-extensions
                image-file-extensions
                log?]}}
  (->mk [_ _ctx]
    (let [manager (->asset-manager)
          sound-files   (search-files folder sound-file-extensions)
          texture-files (search-files folder image-file-extensions)]
      (load-assets! manager sound-files   Sound   log?)
      (load-assets! manager texture-files Texture log?)
      (.finishLoading manager)
      {:manager manager
       :sound-files sound-files
       :texture-files texture-files})))

(defcomponent :context/effect-handler
  (->mk [[_ [game-loop-mode record-transactions?]] _ctx]
    (case game-loop-mode
      :game-loop/normal (when record-transactions?
                          (clear-recorded-txs!)
                          (.bindRoot #'record-txs? true))
      :game-loop/replay (do
                         (assert record-txs?)
                         (.bindRoot #'record-txs? false)
                         ;(println "Initial entity txs:")
                         ;(ctx/summarize-txs ctx (ctx/frame->txs ctx 0))
                         ))
    nil))

(defcomponent :context/graphics
  {:data [:map [:cursors :default-font :views]]
   :let {:keys [views default-font cursors]}}
  (->mk [_ _ctx]
    (map->Graphics
     (let [batch (SpriteBatch.)]
       (merge {:batch batch}
              (->shape-drawer batch)
              (->default-font default-font)
              (->views views)
              (->cursors cursors)))))

  (destroy! [[_ {:keys [batch shape-drawer-texture default-font cursors]}]]
    (dispose batch)
    (dispose shape-drawer-texture)
    (dispose default-font)
    (run! dispose (vals cursors))))

(defcomponent ctx-time
  (->mk [_ _ctx]
    {:elapsed 0
     :logic-frame 0}))

(defcomponent :context/vis-ui
  {:data [:enum [:skin-scale/x1 :skin-scale/x2]]
   :let skin-scale}
  (->mk [_ _ctx]
    (check-cleanup-visui!)
    (VisUI/load (case skin-scale
                  :skin-scale/x1 VisUI$SkinScale/X1
                  :skin-scale/x2 VisUI$SkinScale/X2))
    (font-enable-markup!)
    (set-tooltip-config!)
    :loaded)

  (destroy! [_]
    (VisUI/dispose)))

(defcomponent context-ecs
  (->mk [_ _ctx]
    {}))

;;;;

(when add-metadoc? (add-metadoc!))
