(ns components.context.vis-ui
  (:require [gdx.input :as input]
            [gdx.scene2d.stage :as stage]
            core.image
            [core.component :refer [defcomponent] :as component]
            [core.context :as ctx]
            [core.scene2d.actor :as actor]
            [core.scene2d.group :as group]
            [core.scene2d.ui.table :as table]
            [core.scene2d.ui.widget-group :refer [pack!]])
  (:import com.badlogic.gdx.graphics.g2d.TextureRegion
           (com.badlogic.gdx.utils Align Scaling)
           (com.badlogic.gdx.scenes.scene2d Actor Group Stage)
           (com.badlogic.gdx.scenes.scene2d.ui Image Button Label Table WidgetGroup Stack ButtonGroup HorizontalGroup VerticalGroup Window)
           (com.badlogic.gdx.scenes.scene2d.utils ChangeListener TextureRegionDrawable Drawable)
           (com.kotcrab.vis.ui VisUI VisUI$SkinScale)
           (com.kotcrab.vis.ui.widget VisTextButton VisCheckBox VisSelectBox VisImage VisImageButton VisTextField VisWindow VisTable VisLabel VisSplitPane Tooltip VisScrollPane)))

(defn- check-cleanup-visui! []
  ; app crashes during startup before VisUI/dispose and we do clojure.tools.namespace.refresh-> gui elements not showing.
  ; => actually there is a deeper issue at play
  ; we need to dispose ALL resources which were loaded already ...
  (when (VisUI/isLoaded)
    (VisUI/dispose)))

; TODO font either too big or too small ;;;;
; https://stackoverflow.com/questions/45227586/libgdx-changing-vis-ui-font-using-scene-2d-doesnt-work-is-there-an-alternati
; 1. copy skin files into my folder:
; https://github.com/kotcrab/vis-ui/tree/master/ui/src/main/resources/com/kotcrab/vis/ui/skin
; https://github.com/raeleus/skin-composer/wiki
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
    true)

  (component/destroy [_]
    (VisUI/dispose)))

; TODO not disposed anymore... screens are sub-level.... look for dispose stuff also in @ cdq! FIXME
(defcomponent :screens/stage-screen
  {:let {:keys [stage sub-screen]}}
  (component/enter [_ context]
    (input/set-processor! stage)
    (component/enter sub-screen context))

  (component/exit [_ context]
    (input/set-processor! nil)
    (component/exit sub-screen context))

  (component/render! [_ app-state]
    ; stage act first so user-screen calls change-screen -> is the end of frame
    ; otherwise would need render-after-stage
    ; or on change-screen the stage of the current screen would still .act
    (stage/act! stage)
    (swap! app-state #(component/render-ctx sub-screen %))
    (stage/draw stage)))

(defn- find-actor-with-id [^Group group id]
  (let [actors (.getChildren group)
        ids (keep actor/id actors)]
    (assert (or (empty? ids)
                (apply distinct? ids)) ; TODO could check @ add
            (str "Actor ids are not distinct: " (vec ids)))
    (first (filter #(= id (actor/id %))
                   actors))))

(defn- ->stage [viewport batch]
  (proxy [Stage clojure.lang.ILookup] [viewport batch]
    (valAt
      ([id]
       (find-actor-with-id (stage/root this) id))
      ([id not-found]
       (or (find-actor-with-id (stage/root this) id)
           not-found)))))

(extend-type core.context.Context
  core.context/Stage
  (->stage [{{:keys [gui-view batch]} :context/graphics} actors]
    (let [stage (->stage (:viewport gui-view) batch)]
      (stage/add-actors! stage actors)
      stage))

  (get-stage [context]
    (:stage ((ctx/current-screen context) 1)))

  (mouse-on-stage-actor? [context]
    (stage/hit (ctx/get-stage context)
               (ctx/gui-mouse-position context)
               :touchable? true))

  (add-to-stage! [ctx actor]
    (-> ctx
        ctx/get-stage
        (stage/add-actor! actor))))

(defn- ->change-listener [{:keys [context/state]} on-clicked]
  (proxy [ChangeListener] []
    (changed [event actor]
      (swap! state #(-> %
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
    (pack! widget-group))
  widget-group)

(defn- set-opts [actor opts]
  (set-actor-opts actor opts)
  (when (instance? Table actor)
    (table/set-table-opts actor opts)) ; before widget-group-opts so pack is packing rows
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

(defmethod ->vis-image core.image.Image
  [{:keys [^TextureRegion texture-region]}]
  (VisImage. texture-region))

(defmacro proxy-ILookup
  "For actors inheriting from Group."
  [class args]
  `(proxy [~class clojure.lang.ILookup] ~args
     (valAt
       ([id#]
        (find-actor-with-id ~'this id#))
       ([id# not-found#]
        (or (find-actor-with-id ~'this id#) not-found#)))))

(extend-type core.context.Context
  core.context/Widgets
  (->actor [{:keys [context/state]} {:keys [draw act]}]
    (proxy [Actor] []
      (draw [_batch _parent-alpha]
        (when draw
          (let [ctx @state
                g (assoc (:context/graphics ctx) :unit-scale 1)]
            (draw g ctx))))
      (act [_delta]
        (when act
          (act @state)))))

  (->group [_ {:keys [actors] :as opts}]
    (let [group (proxy-ILookup Group [])]
      (run! #(group/add-actor! group %) actors)
      (set-opts group opts)))

  (->horizontal-group [_ {:keys [space pad]}]
    (let [group (proxy-ILookup HorizontalGroup [])]
      (when space (.space group (float space)))
      (when pad   (.pad   group (float pad)))
      group))

  (->vertical-group [_ actors]
    (let [group (proxy-ILookup VerticalGroup [])]
      (run! #(group/add-actor! group %) actors)
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
     (core.context/->image-button context image on-clicked {}))
    ([context image on-clicked {:keys [dimensions]}]
     (let [drawable (TextureRegionDrawable. ^TextureRegion (:texture-region image))
           button (VisImageButton. drawable)]
       (when-let [[w h] dimensions]
         (.setMinSize drawable (float w) (float h)))
       (.addListener button (->change-listener context on-clicked))
       button)))

  (->table ^Table [_ opts]
    (-> (proxy-ILookup VisTable [])
        (set-opts opts)))

  (->window [_ {:keys [title modal? close-button? center? close-on-escape?] :as opts}]
    (-> (let [window (doto (proxy-ILookup VisWindow [^String title true]) ; true = showWindowBorder
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
    (proxy-ILookup Stack [(into-array Actor actors)]))

  ; TODO widget also make, for fill parent
  (->image-widget [_ object {:keys [scaling align fill-parent?] :as opts}]
    (-> (let [^Image image (->vis-image object)]
          (when (= :center align) (.setAlign image Align/center))
          (when (= :fill scaling) (.setScaling image Scaling/fill))
          (when fill-parent? (.setFillParent image true))
          image)
        (set-opts opts)))

  ; => maybe with VisImage not necessary anymore?
  (->texture-region-drawable [_ ^TextureRegion texture-region]
    (TextureRegionDrawable. texture-region))

  (->scroll-pane [_ actor]
    (let [scroll-pane (VisScrollPane. actor)]
      (.setFlickScroll scroll-pane false)
      (.setFadeScrollBars scroll-pane false)
      scroll-pane)))
