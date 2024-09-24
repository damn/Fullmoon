(ns core.ctx.ui
  (:require [core.ctx :refer :all]
            [core.component :as component]
            core.graphics.image
            [core.graphics.views :refer [gui-viewport-width gui-viewport-height]]
            [core.ui.actor :as actor]
            [core.ui.group :as group])
  (:import com.badlogic.gdx.graphics.g2d.TextureRegion
           (com.badlogic.gdx.utils Align Scaling)
           (com.badlogic.gdx.scenes.scene2d Actor Group)
           (com.badlogic.gdx.scenes.scene2d.ui Label Image Button Table Cell WidgetGroup Stack ButtonGroup HorizontalGroup VerticalGroup Window)
           (com.badlogic.gdx.scenes.scene2d.utils ChangeListener TextureRegionDrawable Drawable)
           (com.kotcrab.vis.ui VisUI VisUI$SkinScale)
           (com.kotcrab.vis.ui.widget Tooltip VisTextButton VisCheckBox VisSelectBox VisImage VisImageButton VisTextField VisWindow VisTable VisLabel VisSplitPane VisScrollPane Separator)))

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

(defcomponent :context/vis-ui
  {:data [:enum [:skin-scale/x1 :skin-scale/x2]]
   :let skin-scale}
  (component/create [_ _ctx]
    (check-cleanup-visui!)
    (VisUI/load (case skin-scale
                  :skin-scale/x1 VisUI$SkinScale/X1
                  :skin-scale/x2 VisUI$SkinScale/X2))
    (font-enable-markup!)
    (set-tooltip-config!)
    :loaded)

  (component/destroy! [_]
    (VisUI/dispose)))

(defn cells [^Table table]
  (.getCells table))

(defn add!
  "Adds a new cell to the table with the specified actor."
  [^Table table actor]
  (.add table ^Actor actor))

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
  (when id   (actor/set-id!   actor id))
  (when name (actor/set-name! actor name))
  (when (contains? opts :visible?)  (actor/set-visible! actor visible?))
  (when touchable (actor/set-touchable! actor touchable))
  (when-let [[x y] center-position] (actor/set-center!   actor x y))
  (when-let [[x y] position]        (actor/set-position! actor x y))
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

(defmethod ->vis-image core.graphics.image.Image
  [{:keys [^TextureRegion texture-region]}]
  (VisImage. texture-region))

(defn ->actor [{:keys [draw act]}]
  (proxy [Actor] []
    (draw [_batch _parent-alpha]
      (when draw
        (let [ctx @app-state
              g (assoc (:context/graphics ctx) :unit-scale 1)]
          (draw g ctx))))
    (act [_delta]
      (when act
        (act @app-state)))))

(defn ->group [{:keys [actors] :as opts}]
  (let [group (group/proxy-ILookup Group [])]
    (run! #(group/add-actor! group %) actors)
    (set-opts group opts)))

(defn ->horizontal-group [{:keys [space pad]}]
  (let [group (group/proxy-ILookup HorizontalGroup [])]
    (when space (.space group (float space)))
    (when pad   (.pad   group (float pad)))
    group))

(defn ->vertical-group [actors]
  (let [group (group/proxy-ILookup VerticalGroup [])]
    (run! #(group/add-actor! group %) actors)
    group))

(defn ->button-group [{:keys [max-check-count min-check-count]}]
  (let [button-group (ButtonGroup.)]
    (.setMaxCheckCount button-group max-check-count)
    (.setMinCheckCount button-group min-check-count)
    button-group))

(defn ->text-button [text on-clicked]
  (let [button (VisTextButton. ^String text)]
    (.addListener button (->change-listener on-clicked))
    button))

(defn ->check-box
  "on-clicked is a fn of one arg, taking the current isChecked state"
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
  (-> (group/proxy-ILookup VisTable [])
      (set-opts opts)))

(defn ->window ^VisWindow [{:keys [title modal? close-button? center? close-on-escape?] :as opts}]
  (-> (let [window (doto (group/proxy-ILookup VisWindow [^String title true]) ; true = showWindowBorder
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
  (group/proxy-ILookup Stack [(into-array Actor actors)]))

; TODO widget also make, for fill parent
(defn ->image-widget
  "Takes either an image or drawable. Opts are :scaling, :align and actor opts."
  [object {:keys [scaling align fill-parent?] :as opts}]
  (-> (let [^Image image (->vis-image object)]
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
      (and (actor/parent actor)
           (button-class? (actor/parent actor)))))

(defn window-title-bar?
  "Returns true if the actor is a window title bar."
  [actor]
  (when (instance? Label actor)
    (when-let [parent (actor/parent actor)]
      (when-let [parent (actor/parent parent)]
        (and (instance? VisWindow parent)
             (= (.getTitleLabel ^Window parent) actor))))))
