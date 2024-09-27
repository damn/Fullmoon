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
         "ctx/operation"
         "ctx/app"
         "ctx/types"
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
