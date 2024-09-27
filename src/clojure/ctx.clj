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
         "ctx/entity"
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

(defn create-vs
  "Creates a map for every component with map entries `[k (->mk [k v] ctx)]`."
  [components ctx]
  (reduce (fn [m [k v]]
            (assoc m k (->mk [k v] ctx)))
          {}
          components))

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
