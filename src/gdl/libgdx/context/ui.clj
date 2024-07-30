(ns ^:no-doc gdl.libgdx.context.ui
  (:require [core.component :as component]
            [gdl.app :refer [current-context]]
            [gdl.context :as ctx]
            [gdl.scene2d.actor :as actor :refer [parent]]
            [gdl.scene2d.group :refer [add-actor!]]
            gdl.scene2d.ui.button
            gdl.scene2d.ui.button-group
            gdl.scene2d.ui.label
            [gdl.scene2d.ui.table :refer [add-rows!]]
            gdl.scene2d.ui.cell
            gdl.scene2d.ui.text-field
            [gdl.scene2d.ui.widget-group :refer [pack!]]
            gdl.scene2d.ui.window
            gdl.libgdx.context.image-drawer-creator)
  (:import com.badlogic.gdx.graphics.g2d.TextureRegion
           (com.badlogic.gdx.utils Align Scaling)
           (com.badlogic.gdx.scenes.scene2d Actor Group Touchable)
           (com.badlogic.gdx.scenes.scene2d.ui Image Button Label Table Cell WidgetGroup Stack ButtonGroup HorizontalGroup VerticalGroup Window)
           (com.badlogic.gdx.scenes.scene2d.utils ChangeListener TextureRegionDrawable Drawable)
           (com.kotcrab.vis.ui VisUI VisUI$SkinScale)
           (com.kotcrab.vis.ui.widget VisTextButton VisCheckBox VisSelectBox VisImage VisImageButton VisTextField VisWindow VisTable VisLabel VisSplitPane Tooltip VisScrollPane)))

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

(component/def :gdl.libgdx.context/ui {}
  _
  (ctx/create [_ _ctx]
    (check-cleanup-visui!)
    (VisUI/load)
    (font-enable-markup!)
    (set-tooltip-config!)
    true)

  (ctx/destroy [_ _ctx]
    (VisUI/dispose)))

(defn- ->change-listener [_ on-clicked]
  (proxy [ChangeListener] []
    (changed [event actor]
      (on-clicked (assoc @current-context :actor actor)))))

; candidate for opts: :tooltip
(defn- set-actor-opts [actor {:keys [id name visible? touchable center-position position] :as opts}]
  (when id   (actor/set-id!   actor id))
  (when name (actor/set-name! actor name))
  (when (contains? opts :visible?)  (actor/set-visible! actor visible?))
  (when touchable (actor/set-touchable! actor touchable))
  (when-let [[x y] center-position] (actor/set-center!   actor x y))
  (when-let [[x y] position]        (actor/set-position! actor x y))
  actor)

; add opts
; image-button
; group (also elements)
; stack

; TODO docstrings for this !
(defn- set-cell-opts [^Cell cell opts]
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

(comment
 ; fill parent & pack is from Widget TODO
 com.badlogic.gdx.scenes.scene2d.ui.Widget
 ; about .pack :
 ; Generally this method should not be called in an actor's constructor because it calls Layout.layout(), which means a subclass would have layout() called before the subclass' constructor. Instead, in constructors simply set the actor's size to Layout.getPrefWidth() and Layout.getPrefHeight(). This allows the actor to have a size at construction time for more convenient use with groups that do not layout their children.
 )

(defn- set-widget-group-opts [^WidgetGroup widget-group {:keys [fill-parent? pack?]}]
  (.setFillParent widget-group (boolean fill-parent?)) ; <- actor? TODO
  (when pack?
    (pack! widget-group))
  widget-group)

(defn- set-table-opts [^Table table {:keys [rows cell-defaults]}]
  (set-cell-opts (.defaults table) cell-defaults)
  (add-rows! table rows))

(defn- set-opts [actor opts]
  (set-actor-opts actor opts)
  (when (instance? Table actor)       (set-table-opts        actor opts)) ; before widget-group-opts so pack is packing rows
  (when (instance? WidgetGroup actor) (set-widget-group-opts actor opts))
  actor)

#_(defn- add-window-close-button [^Window window]
    (.add (.getTitleTable window)
          (text-button "x" #(.setVisible window false)))
    window)

(defn- find-actor-with-id [^Group group id]
  (let [actors (.getChildren group)
        ids (keep actor/id actors)]
    (assert (or (empty? ids)
                (apply distinct? ids)) ; TODO could check @ add
            (str "Actor ids are not distinct: " (vec ids)))
    (first (filter #(= id (actor/id %))
                   actors))))

(defmulti ^:private ->vis-image type)

(defmethod ->vis-image Drawable [^Drawable drawable]
  (VisImage. drawable))

(defmethod ->vis-image gdl.libgdx.context.image_drawer_creator.Image
  [{:keys [^TextureRegion texture]}]
  (VisImage. texture))

(extend-type gdl.context.Context
  gdl.context/Widgets
  (->actor [_ {:keys [draw act]}]
    (proxy [Actor] []
      (draw [_batch _parent-alpha]
        (when draw
          (draw @current-context)))
      (act [_delta]
        (when act
          (act @current-context)))))

  (->group [_ {:keys [actors] :as opts}]
    (let [group (proxy [Group clojure.lang.ILookup] []
                  (valAt
                    ([id]
                     (find-actor-with-id this id))
                    ([id not-found]
                     (or (find-actor-with-id this id) not-found))))]
      (run! #(add-actor! group %) actors)
      (set-opts group opts)))

  (->horizontal-group [_ {:keys [space pad]}]
    (let [group (proxy [HorizontalGroup clojure.lang.ILookup] []
                  (valAt
                    ([id]
                     (find-actor-with-id this id))
                    ([id not-found]
                     (or (find-actor-with-id this id) not-found))))]
      (when space (.space group (float space)))
      (when pad   (.pad   group (float pad)))
      group))

  (->vertical-group [_ actors]
    (let [group (proxy [VerticalGroup clojure.lang.ILookup] []
                  (valAt
                    ([id]
                     (find-actor-with-id this id))
                    ([id not-found]
                     (or (find-actor-with-id this id) not-found))))]
      (run! #(add-actor! group %) actors)
      group))

  (->button-group [_ {:keys [max-check-count min-check-count]}]
    (let [button-group (ButtonGroup.)]
      (.setMaxCheckCount button-group max-check-count)
      (.setMinCheckCount button-group min-check-count)
      button-group))

  (->text-button [context text on-clicked]
    (let [button (VisTextButton. ^String text)]
      (.addListener button (->change-listener context on-clicked))
      button))

  (->check-box [context text on-clicked checked?]
    (let [^Button button (VisCheckBox. ^String text)]
      (.setChecked button checked?)
      (.addListener button
                    (proxy [ChangeListener] []
                      (changed [event ^Button actor]
                        (on-clicked (.isChecked actor)))))
      button))

  (->select-box [_ {:keys [items selected]}]
    (doto (VisSelectBox.)
      (.setItems (into-array items))
      (.setSelected selected)))

  ; TODO give directly texture-region
  ; TODO check how to make toggle-able ? with hotkeys for actionbar trigger ?
  (->image-button
    ([context image on-clicked]
     (gdl.context/->image-button context image on-clicked {}))
    ([context image on-clicked {:keys [dimensions]}]
     (let [drawable (TextureRegionDrawable. ^TextureRegion (:texture image))
           button (VisImageButton. drawable)]
       (when-let [[w h] dimensions]
         (.setMinSize drawable (float w) (float h)))
       (.addListener button (->change-listener context on-clicked))
       button)))

  (->table ^Table [_ opts]
    (-> (proxy [VisTable clojure.lang.ILookup] []
          (valAt
            ([id]
             (find-actor-with-id this id))
            ([id not-found]
             (or (find-actor-with-id this id) not-found))))
        (set-opts opts)))

  (->window [_ {:keys [title modal? close-button? center? close-on-escape?] :as opts}]
    (-> (let [window (doto (proxy [VisWindow clojure.lang.ILookup] [^String title true] ; true = showWindowBorder
                             (valAt
                               ([id]
                                (find-actor-with-id this id))
                               ([id not-found]
                                (or (find-actor-with-id this id) not-found))))
                       (.setModal (boolean modal?)))]
          (when close-button?    (.addCloseButton window))
          (when center?          (.centerWindow   window))
          (when close-on-escape? (.closeOnEscape  window))
          window)
        (set-opts opts)))

  (->label [_ text]
    (VisLabel. ^CharSequence text))

  (->text-field [_ ^String text opts]
    (-> (VisTextField. text)
        (set-opts opts)))

  ; TODO is not decendend of SplitPane anymore => check all type hints here
  (->split-pane [_ {:keys [^Actor first-widget
                           ^Actor second-widget
                           ^Boolean vertical?] :as opts}]
    (-> (VisSplitPane. first-widget second-widget vertical?)
        (set-actor-opts opts)))

  (->stack [_ actors]
    (proxy [Stack clojure.lang.ILookup] [(into-array Actor actors)]
      (valAt
        ([id]
         (find-actor-with-id this id))
        ([id not-found]
         (or (find-actor-with-id this id) not-found)))))

  ; TODO widget also make, for fill parent
  (->image-widget [_ object {:keys [scaling align fill-parent?] :as opts}]
    (-> (let [^Image image (->vis-image object)]
          (when (= :center align) (.setAlign image Align/center))
          (when (= :fill scaling) (.setScaling image Scaling/fill))
          (when fill-parent? (.setFillParent image true))
          image)
        (set-opts opts)))

  ; => maybe with VisImage not necessary anymore?
  (->texture-region-drawable [_ ^TextureRegion texture]
    (TextureRegionDrawable. texture))

  (->scroll-pane [_ actor]
    (let [scroll-pane (VisScrollPane. actor)]
      (.setFlickScroll scroll-pane false)
      (.setFadeScrollBars scroll-pane false)
      scroll-pane)))

(extend-type Cell
  gdl.scene2d.ui.cell/Cell
  (set-actor! [cell actor]
    (.setActor cell actor)))

(extend-type Table
  gdl.scene2d.ui.table/Table
  (cells [table]
    (.getCells table))

  (add-rows! [table rows]
    (doseq [row rows]
      (doseq [props-or-actor row]
        (if (map? props-or-actor)
          (-> (.add table ^Actor (:actor props-or-actor))
              (set-cell-opts (dissoc props-or-actor :actor)))
          (.add table ^Actor props-or-actor)))
      (.row table))
    table)

  (add! [table actor]
    (.add table ^Actor actor)))

(extend-type Label
  gdl.scene2d.ui.label/Label
  (set-text! [^Label label ^CharSequence text]
    (.setText label text)))

(extend-type VisTextField
  gdl.scene2d.ui.text-field/TextField
  (text [text-field]
    (.getText text-field)))

(extend-type Group
  gdl.scene2d.group/Group
  (children [group]
    (seq (.getChildren group)))

  (clear-children! [group]
    (.clearChildren group))

  (find-actor-with-id [^Group group id]
    (find-actor-with-id group id))

  (add-actor! [group actor]
    (.addActor group actor)))

(extend-type Actor
  gdl.scene2d.actor/Actor
  (id [actor] (.getUserObject actor))
  (set-id! [actor id] (.setUserObject actor id))
  (set-name! [actor name] (.setName actor name))
  (name [actor] (.getName actor))
  (visible? [actor] (.isVisible actor))
  (set-visible! [actor bool] (.setVisible actor (boolean bool)))
  (toggle-visible! [actor]
    (.setVisible actor (not (.isVisible actor))))
  (set-position! [actor x y] (.setPosition actor x y))
  (set-center! [actor x y]
    (.setPosition actor
                  (- x (/ (.getWidth actor) 2))
                  (- y (/ (.getHeight actor) 2))))
  (set-width! [actor width] (.setWidth actor width))
  (set-height! [actor height] (.setHeight actor height))
  (get-x [actor] (.getY actor))
  (get-y [actor] (.getX actor))
  (width [actor] (.getWidth actor))
  (height [actor] (.getHeight actor))
  (set-touchable! [actor touchable]
    (.setTouchable actor (case touchable
                           :children-only Touchable/childrenOnly
                           :disabled      Touchable/disabled
                           :enabled       Touchable/enabled)))
  (add-listener! [actor listener]
    (.addListener actor listener))
  (remove! [actor]
    (.remove actor))
  (parent [actor]
    (.getParent actor))

  (add-tooltip! [actor tooltip-text]
    (let [text? (string? tooltip-text)
          label (VisLabel. (if text? tooltip-text ""))
          tooltip (proxy [Tooltip] []
                    ; hooking into getWidth because at
                    ; https://github.com/kotcrab/vis-ui/blob/master/ui/src/main/java/com/kotcrab/vis/ui/widget/Tooltip.java#L271
                    ; when tooltip position gets calculated we setText (which calls pack) before that
                    ; so that the size is correct for the newly calculated text.
                    (getWidth []
                      (let [^Tooltip this this]
                        (when-not text?
                          (when-let [ctx @current-context]  ; initial tooltip creation when app context is getting built.
                            (.setText this (str (tooltip-text ctx)))))
                        (proxy-super getWidth))))]
      (.setAlignment label Align/center)
      (.setTarget  tooltip ^Actor actor)
      (.setContent tooltip ^Actor label)))

  (remove-tooltip! [actor]
    (Tooltip/removeTooltip actor))

  (find-ancestor-window [actor]
    (if-let [p (parent actor)]
      (if (instance? Window p)
        p
        (actor/find-ancestor-window p))
      (throw (Error. (str "Actor has no parent window " actor)))))

  (pack-ancestor-window! [actor]
    (pack! (actor/find-ancestor-window actor))))

(extend-type ButtonGroup
  gdl.scene2d.ui.button-group/ButtonGroup
  (clear! [button-group]
    (.clear button-group))
  (add! [button-group button]
    (.add button-group ^Button button))
  (checked [button-group]
    (.getChecked button-group))
  (remove! [button-group button]
    (.remove button-group ^Button button)))

(extend-type WidgetGroup
  gdl.scene2d.ui.widget-group/WidgetGroup
  (pack! [group]
    (.pack group)))

(defn- button-class? [actor]
  (some #(= Button %) (supers (class actor))))

(extend-type Actor
  gdl.scene2d.ui.button/Actor
  (button? [actor]
    (or (button-class? actor)
        (and (parent actor)
             (button-class? (parent actor))))))

(extend-type Actor
  gdl.scene2d.ui.window/Actor
  (window-title-bar? [actor]
    (when (instance? Label actor)
      (when-let [prnt (parent actor)]
        (when-let [prnt (parent prnt)]
          (and (instance? VisWindow prnt)
               (= (.getTitleLabel ^Window prnt) actor)))))))
