(in-ns 'clojure.ctx)

(defn- check-cleanup-visui! []
  ; app crashes during startup before VisUI/dispose and we do clojure.tools.namespace.refresh-> gui elements not showing.
  ; => actually there is a deeper issue at play
  ; we need to dispose ALL resources which were loaded already ...
  (when (VisUI/isLoaded)
    (VisUI/dispose)))

(defn- font-enable-markup! []
  (-> (VisUI/getSkin)
      (.getFont "default-font")
      .getData
      .markupEnabled
      (set! true)))

(defn- set-tooltip-config! []
  (set! Tooltip/DEFAULT_APPEAR_DELAY_TIME (float 0))
  ;(set! Tooltip/DEFAULT_FADE_TIME (float 0.3))
  ;Controls whether to fade out tooltip when mouse was moved. (default false)
  ;(set! Tooltip/MOUSE_MOVED_FADEOUT true)
  )

;; gdx scene2d helper

(defn actor-id [^Actor actor]
  (.getUserObject actor))

(defn set-id! [^Actor actor id]
  (.setUserObject actor id))

(defn set-name! [^Actor actor name]
  (.setName actor name))

(defn actor-name [^Actor actor]
  (.getName actor))

(defn visible? [^Actor actor] ; used
  (.isVisible actor))

(defn set-visible! [^Actor actor bool]
  (.setVisible actor (boolean bool)))

(defn toggle-visible! [actor] ; used
  (set-visible! actor (not (visible? actor))))

(defn set-position! [^Actor actor x y]
  (.setPosition actor x y))

(defn set-center! [^Actor actor x y]
  (set-position! actor
                 (- x (/ (.getWidth actor) 2))
                 (- y (/ (.getHeight actor) 2))))

(defn set-touchable!
  ":children-only, :disabled or :enabled."
  [^Actor actor touchable]
  (.setTouchable actor (case touchable
                         :children-only Touchable/childrenOnly
                         :disabled      Touchable/disabled
                         :enabled       Touchable/enabled)))

(defn add-listener! [^Actor actor listener]
  (.addListener actor listener))

(defn remove!
  "Removes this actor from its parent, if it has a parent."
  [^Actor actor]
  (.remove actor))

(defn parent
  "Returns the parent actor, or null if not in a group."
  [^Actor actor]
  (.getParent actor))

(def app-state
  "An atom referencing the current Context. Only use by ui-callbacks or for development/debugging.
  Use only with (post-runnable! & exprs) for making manual changes to the ctx."
  (atom nil))

(defn add-tooltip!
  "tooltip-text is a (fn [context] ) or a string. If it is a function will be-recalculated every show."
  [^Actor actor tooltip-text]
  (let [text? (string? tooltip-text)
        label (VisLabel. (if text? tooltip-text ""))
        tooltip (proxy [Tooltip] []
                  ; hooking into getWidth because at
                  ; https://github.com/kotcrab/vis-blob/master/ui/src/main/java/com/kotcrab/vis/ui/widget/Tooltip.java#L271
                  ; when tooltip position gets calculated we setText (which calls pack) before that
                  ; so that the size is correct for the newly calculated text.
                  (getWidth []
                    (let [^Tooltip this this]
                      (when-not text?
                        (when-let [ctx @app-state]  ; initial tooltip creation when app context is getting built.
                          (.setText this (str (tooltip-text ctx)))))
                      (proxy-super getWidth))))]
    (.setAlignment label Align/center)
    (.setTarget  tooltip ^Actor actor)
    (.setContent tooltip ^Actor label)))

(defn remove-tooltip! [^Actor actor]
  (Tooltip/removeTooltip actor))

(defn find-ancestor-window ^Window [^Actor actor]
  (if-let [p (parent actor)]
    (if (instance? Window p)
      p
      (find-ancestor-window p))
    (throw (Error. (str "Actor has no parent window " actor)))))

(defn pack-ancestor-window! [^Actor actor]
  (.pack (find-ancestor-window actor)))

(defn children
  "Returns an ordered list of child actors in this group."
  [^Group group]
  (seq (.getChildren group)))

(defn clear-children!
  "Removes all actors from this group and unfocuses them."
  [^Group group]
  (.clearChildren group))

(defn add-actor!
  "Adds an actor as a child of this group, removing it from its previous parent. If the actor is already a child of this group, no changes are made."
  [^Group group actor]
  (.addActor group actor))

(defn- find-actor-with-id [group id]
  (let [actors (children group)
        ids (keep actor-id actors)]
    (assert (or (empty? ids)
                (apply distinct? ids)) ; TODO could check @ add
            (str "Actor ids are not distinct: " (vec ids)))
    (first (filter #(= id (actor-id %))
                   actors))))

; TODO not disposed anymore... screens are sub-level.... look for dispose stuff also in @ cdq! FIXME
(defcomponent :screens/stage
  {:let {:keys [^Stage stage sub-screen]}}
  (screen-enter [_ context]
    (set-input-processor! stage)
    (screen-enter sub-screen context))

  (screen-exit [_ context]
    (set-input-processor! nil)
    (screen-exit sub-screen context))

  (screen-render! [_]
    ; stage act first so user-screen calls change-screen -> is the end of frame
    ; otherwise would need render-after-stage
    ; or on change-screen the stage of the current screen would still .act
    (.act stage)
    (swap! app-state #(screen-render sub-screen %))
    (.draw stage)))

(defn- ->stage* ^Stage [viewport batch]
  (proxy [Stage clojure.lang.ILookup] [viewport batch]
    (valAt
      ([id]
       (find-actor-with-id (.getRoot ^Stage this) id))
      ([id not-found]
       (or (find-actor-with-id (.getRoot ^Stage this) id) not-found)))))

(defn ->stage
  "Stage implements clojure.lang.ILookup (get) on actor id."
  [{{:keys [gui-view batch]} :context/graphics} actors]
  (let [stage (->stage* (:viewport gui-view) batch)]
    (run! #(.addActor stage %) actors)
    stage))

(defn stage-get ^Stage [context]
  (:stage ((current-screen context) 1)))

(defn mouse-on-actor? [context]
  (let [[x y] (gui-mouse-position context)
        touchable? true]
    (.hit (stage-get context) x y touchable?)))

(defn stage-add! [ctx actor]
  (-> ctx stage-get (.addActor actor))
  ctx)

(defn set-cell-opts [^Cell cell opts]
  (doseq [[option arg] opts]
    (case option
      :fill-x?    (.fillX     cell)
      :fill-y?    (.fillY     cell)
      :expand?    (.expand    cell)
      :expand-x?  (.expandX   cell)
      :expand-y?  (.expandY   cell)
      :bottom?    (.bottom    cell)
      :colspan    (.colspan   cell (int arg))
      :pad        (.pad       cell (float arg))
      :pad-top    (.padTop    cell (float arg))
      :pad-bottom (.padBottom cell (float arg))
      :width      (.width     cell (float arg))
      :height     (.height    cell (float arg))
      :right?     (.right     cell)
      :left?      (.left      cell))))

(defn add-rows!
  "rows is a seq of seqs of columns.
  Elements are actors or nil (for just adding empty cells ) or a map of
  {:actor :expand? :bottom?  :colspan int :pad :pad-bottom}. Only :actor is required."
  [^Table table rows]
  (doseq [row rows]
    (doseq [props-or-actor row]
      (cond
       (map? props-or-actor) (-> (.add table ^Actor (:actor props-or-actor))
                                 (set-cell-opts (dissoc props-or-actor :actor)))
       :else (.add table ^Actor props-or-actor)))
    (.row table))
  table)

(defn set-table-opts [^Table table {:keys [rows cell-defaults]}]
  (set-cell-opts (.defaults table) cell-defaults)
  (add-rows! table rows))

(defn ->horizontal-separator-cell [colspan]
  {:actor (Separator. "default")
   :pad-top 2
   :pad-bottom 2
   :colspan colspan
   :fill-x? true
   :expand-x? true})

(defn ->vertical-separator-cell []
  {:actor (Separator. "vertical")
   :pad-top 2
   :pad-bottom 2
   :fill-y? true
   :expand-y? true})

(defn- ->change-listener [on-clicked]
  (proxy [ChangeListener] []
    (changed [event actor]
      (swap! app-state #(-> %
                            (assoc :context/actor actor)
                            on-clicked
                            (dissoc :context/actor))))))

; candidate for opts: :tooltip
(defn- set-actor-opts [actor {:keys [id name visible? touchable center-position position] :as opts}]
  (when id   (set-id!   actor id))
  (when name (set-name! actor name))
  (when (contains? opts :visible?)  (set-visible! actor visible?))
  (when touchable (set-touchable! actor touchable))
  (when-let [[x y] center-position] (set-center!   actor x y))
  (when-let [[x y] position]        (set-position! actor x y))
  actor)

(comment
 ; fill parent & pack is from Widget TODO ( not widget-group ?)
 com.badlogic.gdx.scenes.scene2d.ui.Widget
 ; about .pack :
 ; Generally this method should not be called in an actor's constructor because it calls Layout.layout(), which means a subclass would have layout() called before the subclass' constructor. Instead, in constructors simply set the actor's size to Layout.getPrefWidth() and Layout.getPrefHeight(). This allows the actor to have a size at construction time for more convenient use with groups that do not layout their children.
 )

(defn- set-widget-group-opts [^WidgetGroup widget-group {:keys [fill-parent? pack?]}]
  (.setFillParent widget-group (boolean fill-parent?)) ; <- actor? TODO
  (when pack?
    (.pack widget-group))
  widget-group)

(defn- set-opts [actor opts]
  (set-actor-opts actor opts)
  (when (instance? Table actor)
    (set-table-opts actor opts)) ; before widget-group-opts so pack is packing rows
  (when (instance? WidgetGroup actor)
    (set-widget-group-opts actor opts))
  actor)

#_(defn- add-window-close-button [^Window window]
    (.add (.getTitleTable window)
          (text-button "x" #(.setVisible window false)))
    window)

(defmulti ^:private ->vis-image type)

(defmethod ->vis-image Drawable [^Drawable drawable]
  (VisImage. drawable))

(defmethod ->vis-image Image
  [{:keys [^TextureRegion texture-region]}]
  (VisImage. texture-region))

(defn ->actor
  "[com.badlogic.gdx.scenes.scene2d.Actor](https://javadoc.io/doc/com.badlogicgames.gdx/gdx/latest/com/badlogic/gdx/scenes/scene2d/Actor.html)"
  [{:keys [draw act]}]
  (proxy [Actor] []
    (draw [_batch _parent-alpha]
      (when draw
        (let [ctx @app-state
              g (assoc (:context/graphics ctx) :unit-scale 1)]
          (draw g ctx))))
    (act [_delta]
      (when act
        (act @app-state)))))

(defmacro ^:private proxy-ILookup
  "For actors inheriting from Group."
  [class args]
  `(proxy [~class clojure.lang.ILookup] ~args
     (valAt
       ([id#]
        (find-actor-with-id ~'this id#))
       ([id# not-found#]
        (or (find-actor-with-id ~'this id#) not-found#)))))

(defn ->group [{:keys [actors] :as opts}]
  (let [group (proxy-ILookup Group [])]
    (run! #(add-actor! group %) actors)
    (set-opts group opts)))

(defn ->horizontal-group [{:keys [space pad]}]
  (let [group (proxy-ILookup HorizontalGroup [])]
    (when space (.space group (float space)))
    (when pad   (.pad   group (float pad)))
    group))

(defn ->vertical-group [actors]
  (let [group (proxy-ILookup VerticalGroup [])]
    (run! #(add-actor! group %) actors)
    group))

(defn ->button-group
  "https://javadoc.io/doc/com.badlogicgames.gdx/gdx/latest/com/badlogic/gdx/scenes/scene2d/ui/ButtonGroup.html"
  [{:keys [max-check-count min-check-count]}]
  (let [button-group (ButtonGroup.)]
    (.setMaxCheckCount button-group max-check-count)
    (.setMinCheckCount button-group min-check-count)
    button-group))

(defn ->text-button [text on-clicked]
  (let [button (VisTextButton. ^String text)]
    (.addListener button (->change-listener on-clicked))
    button))

(defn ->check-box
  "on-clicked is a fn of one arg, taking the current isChecked state
  [com.kotcrab.vis.ui.widget.VisCheckBox](https://www.javadoc.io/static/com.kotcrab.vis/vis-ui/1.5.3/com/kotcrab/vis/ui/widget/VisCheckBox.html)"
  [text on-clicked checked?]
  (let [^Button button (VisCheckBox. ^String text)]
    (.setChecked button checked?)
    (.addListener button
                  (proxy [ChangeListener] []
                    (changed [event ^Button actor]
                      (on-clicked (.isChecked actor)))))
    button))

(defn ->select-box [{:keys [items selected]}]
  (doto (VisSelectBox.)
    (.setItems (into-array items))
    (.setSelected selected)))

; TODO give directly texture-region
; TODO check how to make toggle-able ? with hotkeys for actionbar trigger ?
(defn ->image-button
  ([image on-clicked]
   (->image-button image on-clicked {}))

  ([image on-clicked {:keys [scale]}]
   (let [drawable (TextureRegionDrawable. ^TextureRegion (:texture-region image))
         button (VisImageButton. drawable)]
     (when scale
       (let [[w h] (:pixel-dimensions image)]
         (.setMinSize drawable (float (* scale w)) (float (* scale h)))))
     (.addListener button (->change-listener on-clicked))
     button)))

(defn ->table ^Table [opts]
  (-> (proxy-ILookup VisTable [])
      (set-opts opts)))

(defn ->window ^VisWindow [{:keys [title modal? close-button? center? close-on-escape?] :as opts}]
  (-> (let [window (doto (proxy-ILookup VisWindow [^String title true]) ; true = showWindowBorder
                     (.setModal (boolean modal?)))]
        (when close-button?    (.addCloseButton window))
        (when center?          (.centerWindow   window))
        (when close-on-escape? (.closeOnEscape  window))
        window)
      (set-opts opts)))

(defn ->label ^VisLabel [text]
  (VisLabel. ^CharSequence text))

(defn ->text-field [^String text opts]
  (-> (VisTextField. text)
      (set-opts opts)))

; TODO is not decendend of SplitPane anymore => check all type hints here
(defn ->split-pane [{:keys [^Actor first-widget
                            ^Actor second-widget
                            ^Boolean vertical?] :as opts}]
  (-> (VisSplitPane. first-widget second-widget vertical?)
      (set-actor-opts opts)))

(defn ->stack [actors]
  (proxy-ILookup Stack [(into-array Actor actors)]))

; TODO widget also make, for fill parent
(defn ->image-widget
  "Takes either an image or drawable. Opts are :scaling, :align and actor opts."
  [object {:keys [scaling align fill-parent?] :as opts}]
  (-> (let [^com.badlogic.gdx.scenes.scene2d.ui.Image image (->vis-image object)]
        (when (= :center align) (.setAlign image Align/center))
        (when (= :fill scaling) (.setScaling image Scaling/fill))
        (when fill-parent? (.setFillParent image true))
        image)
      (set-opts opts)))

; => maybe with VisImage not necessary anymore?
(defn ->texture-region-drawable [^TextureRegion texture-region]
  (TextureRegionDrawable. texture-region))

(defn ->scroll-pane [actor]
  (let [scroll-pane (VisScrollPane. actor)]
    (.setFlickScroll scroll-pane false)
    (.setFadeScrollBars scroll-pane false)
    scroll-pane))

; TODO set to preferred width/height ??? why layouting not working properly?
; use a tree?
; make example with plain data
(defn ->scroll-pane-cell [ctx rows]
  (let [table (->table {:rows rows
                        :cell-defaults {:pad 1}
                        :pack? true})
        scroll-pane (->scroll-pane table)]
    {:actor scroll-pane
     :width (- (gui-viewport-width ctx) 600) ;(+ (actor/width table) 200)
     :height (- (gui-viewport-height ctx) 100)})) ;(min (- (ctx/gui-viewport-height ctx) 50) (actor/height table))

(defn- button-class? [actor]
  (some #(= Button %) (supers (class actor))))

(defn button?
  "Returns true if the actor or its parent is a button."
  [actor]
  (or (button-class? actor)
      (and (parent actor)
           (button-class? (parent actor)))))

(defn window-title-bar?
  "Returns true if the actor is a window title bar."
  [actor]
  (when (instance? Label actor)
    (when-let [p (parent actor)]
      (when-let [p (parent p)]
        (and (instance? VisWindow p)
             (= (.getTitleLabel ^Window p) actor))))))

(def ^:private image-file "images/moon_background.png")

(defn ->background-image [ctx]
  (->image-widget (->image ctx image-file)
                  {:fill-parent? true
                   :scaling :fill
                   :align :center}))

(defmacro ^:private with-err-str
  "Evaluates exprs in a context in which *out* is bound to a fresh
  StringWriter.  Returns the string created by any nested printing
  calls."
  {:added "1.0"}
  [& body]
  `(let [s# (new java.io.StringWriter)]
     (binding [*err* s#]
       ~@body
       (str s#))))

(defn error-window! [ctx throwable]
  (binding [*print-level* 5]
    (pretty-pst throwable 24))
  (stage-add! ctx (->window {:title "Error"
                             :rows [[(->label (binding [*print-level* 3]
                                                (with-err-str
                                                  (clojure.repl/pst throwable))))]]
                             :modal? true
                             :close-button? true
                             :close-on-escape? true
                             :center? true
                             :pack? true})))

; TODO no window movable type cursor appears here like in player idle
; inventory still working, other stuff not, because custom listener to keypresses ? use actor listeners?
; => input events handling
; hmmm interesting ... can disable @ item in cursor  / moving / etc.

(defn- show-player-modal! [ctx {:keys [title text button-text on-click]}]
  (assert (not (::modal (stage-get ctx))))
  (stage-add! ctx
              ; move into separate fns with params
              ; and othe rstuff into do
              (->window {:title title
                         :rows [[(->label text)]
                                [(->text-button button-text
                                                (fn [ctx]
                                                  (remove! (::modal (stage-get ctx)))
                                                  (on-click ctx)))]]
                         :id ::modal
                         :modal? true
                         :center-position [(/ (gui-viewport-width ctx) 2)
                                           (* (gui-viewport-height ctx) (/ 3 4))]
                         :pack? true})))

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
    (swap! app-state update ctx-msg-player update :counter + (delta-time))
    (when (>= counter duration-seconds)
      (swap! app-state assoc ctx-msg-player nil))))

(defcomponent :widgets/player-message
  (->mk [_ _ctx]
    (->actor {:draw draw-player-message
              :act check-remove-message})))
