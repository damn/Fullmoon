(in-ns 'clojure.ctx)

(defrecord Graphics [batch
                     shape-drawer
                     gui-view
                     world-view
                     default-font
                     unit-scale
                     cursors])

(defprotocol PShapeDrawer
  (draw-ellipse [_ position radius-x radius-y color])
  (draw-filled-ellipse [_ position radius-x radius-y color])
  (draw-circle [_ position radius color])
  (draw-filled-circle [_ position radius color])
  (draw-arc [_ center-position radius start-angle degree color])
  (draw-sector [_ center-position radius start-angle degree color])
  (draw-rectangle [_ x y w h color])
  (draw-filled-rectangle [_ x y w h color])
  (draw-line [_ start-position end-position color])
  (draw-grid [drawer leftx bottomy gridw gridh cellw cellh color])
  (with-shape-line-width [_ width draw-fn]))

(extend-type Graphics
  PShapeDrawer
  (draw-ellipse [{:keys [shape-drawer]} position radius-x radius-y color]
    (set-color! shape-drawer color)
    (sd-ellipse shape-drawer position radius-x radius-y))
  (draw-filled-ellipse [{:keys [shape-drawer]} position radius-x radius-y color]
    (set-color! shape-drawer color)
    (sd-filled-ellipse shape-drawer position radius-x radius-y))
  (draw-circle [{:keys [shape-drawer]} position radius color]
    (set-color! shape-drawer color)
    (sd-circle shape-drawer position radius))
  (draw-filled-circle [{:keys [shape-drawer]} position radius color]
    (set-color! shape-drawer color)
    (sd-filled-circle shape-drawer position radius))
  (draw-arc [{:keys [shape-drawer]} center radius start-angle degree color]
    (set-color! shape-drawer color)
    (sd-arc shape-drawer center radius start-angle degree))
  (draw-sector [{:keys [shape-drawer]} center radius start-angle degree color]
    (set-color! shape-drawer color)
    (sd-sector shape-drawer center radius start-angle degree))
  (draw-rectangle [{:keys [shape-drawer]} x y w h color]
    (set-color! shape-drawer color)
    (sd-rectangle shape-drawer x y w h) )
  (draw-filled-rectangle [{:keys [shape-drawer]} x y w h color]
    (set-color! shape-drawer color)
    (sd-filled-rectangle shape-drawer x y w h))
  (draw-line [{:keys [shape-drawer]} start end color]
    (set-color! shape-drawer color)
    (sd-line shape-drawer start end))
  (draw-grid [{:keys [shape-drawer]} leftx bottomy gridw gridh cellw cellh color]
    (sd-grid shape-drawer leftx bottomy gridw gridh cellw cellh color))
  (with-shape-line-width [{:keys [shape-drawer]} width draw-fn]
    (sd-with-line-width shape-drawer width draw-fn)))

(defprotocol WorldView
  (pixels->world-units [_ pixels])
  (world-unit-scale [_]))

(defprotocol TextDrawer
  (draw-text [_ {:keys [x y text font h-align up? scale]}]
             "font, h-align, up? and scale are optional.
             h-align one of: :center, :left, :right. Default :center.
             up? renders the font over y, otherwise under.
             scale will multiply the drawn text size with the scale."))

(defprotocol ImageDraw
  (draw-image [_ image position])
  (draw-centered-image [_ image position])
  (draw-rotated-centered-image [_ image rotation position]))

(defrecord Image [texture-region
                  pixel-dimensions
                  world-unit-dimensions
                  color]) ; optional

(defn- unit-dimensions [image unit-scale]
  (if (= unit-scale 1)
    (:pixel-dimensions image)
    (:world-unit-dimensions image)))

(defn- scale-dimensions [dimensions scale]
  (mapv (comp float (partial * scale)) dimensions))

(defn- assoc-dimensions
  "scale can be a number for multiplying the texture-region-dimensions or [w h]."
  [{:keys [texture-region] :as image} g scale]
  {:pre [(or (number? scale)
             (and (vector? scale)
                  (number? (scale 0))
                  (number? (scale 1))))]}
  (let [pixel-dimensions (if (number? scale)
                           (scale-dimensions (texture-region-dimensions texture-region) scale)
                           scale)]
    (assoc image
           :pixel-dimensions pixel-dimensions
           :world-unit-dimensions (scale-dimensions pixel-dimensions (world-unit-scale g)))))

(extend-type Graphics
  ImageDraw
  (draw-image [{:keys [batch unit-scale]}
               {:keys [texture-region color] :as image}
               position]
    (draw-texture-region batch
                         texture-region
                         position
                         (unit-dimensions image unit-scale)
                         0 ; rotation
                         color))

  (draw-rotated-centered-image [{:keys [batch unit-scale]}
                                {:keys [texture-region color] :as image}
                                rotation
                                [x y]]
    (let [[w h] (unit-dimensions image unit-scale)]
      (draw-texture-region batch
                           texture-region
                           [(- (float x) (/ (float w) 2))
                            (- (float y) (/ (float h) 2))]
                           [w h]
                           rotation
                           color)))

  (draw-centered-image [this image position]
    (draw-rotated-centered-image this image 0 position)))

(defn- ->image* [g texture-region]
  (-> {:texture-region texture-region}
      (assoc-dimensions g 1)
      map->Image))

(defn ->image [{g :context/graphics :as ctx} file]
  (->image* g (->texture-region (texture ctx file)))) ; TODO why doesnt texture work?

(defn sub-image [{g :context/graphics} {:keys [texture-region]} bounds]
  (->image* g (->texture-region texture-region bounds)))

(defn sprite-sheet [ctx file tilew tileh]
  {:image (->image ctx file)
   :tilew tilew
   :tileh tileh})

(defn sprite
  "x,y index starting top-left"
  [ctx {:keys [image tilew tileh]} [x y]]
  (sub-image ctx image [(* x tilew) (* y tileh) tilew tileh]))

(defn edn->image [{:keys [file sub-image-bounds]} ctx]
  (if sub-image-bounds
    (let [[sprite-x sprite-y] (take 2 sub-image-bounds)
          [tilew tileh]       (drop 2 sub-image-bounds)]
      (sprite ctx
              (sprite-sheet ctx file tilew tileh)
              [(int (/ sprite-x tilew))
               (int (/ sprite-y tileh))]))
    (->image ctx file)))

(defn- ->default-font [default-font]
  {:default-font (or (and default-font (generate-ttf default-font))
                     (gdx-default-font))})

(extend-type Graphics
  TextDrawer
  (draw-text [{:keys [default-font unit-scale batch]}
              {:keys [x y text font h-align up? scale] :as opts}]
    (font-draw (or font default-font) unit-scale batch opts)))

(defn- ->gui-view [{:keys [world-width world-height]}]
  {:unit-scale 1
   :viewport (->gui-viewport world-width world-height)})

(defn- ->world-view [{:keys [world-width world-height tile-size]}]
  (let [unit-scale (/ tile-size)]
    {:unit-scale (float unit-scale)
     :viewport (->world-viewport world-width world-height unit-scale)}))

(defn- ->views [{:keys [gui-view world-view]}]
  {:gui-view (->gui-view gui-view)
   :world-view (->world-view world-view)})

(extend-type Graphics
  WorldView
  (world-unit-scale [{:keys [world-view]}]
    (:unit-scale world-view))

  (pixels->world-units [g pixels]
    (* (int pixels) (world-unit-scale g))))

(defn- gui-viewport   [g] (-> g :gui-view   :viewport))
(defn- world-viewport [g] (-> g :world-view :viewport))

(defn- gui-mouse-position* [g]
  ; TODO mapv int needed?
  (mapv int (unproject-mouse-posi (gui-viewport g))))

(defn- world-mouse-position* [g]
  ; TODO clamping only works for gui-viewport ? check. comment if true
  ; TODO ? "Can be negative coordinates, undefined cells."
  (unproject-mouse-posi (world-viewport g)))

(defn- gr [ctx] (:context/graphics ctx))

(defn gui-mouse-position    [ctx] (gui-mouse-position*             (gr ctx)))
(defn world-mouse-position  [ctx] (world-mouse-position*           (gr ctx)))
(defn gui-viewport-width    [ctx] (vp-world-width  (gui-viewport   (gr ctx))))
(defn gui-viewport-height   [ctx] (vp-world-height (gui-viewport   (gr ctx))))
(defn world-camera          [ctx] (vp-camera       (world-viewport (gr ctx))))
(defn world-viewport-width  [ctx] (vp-world-width  (world-viewport (gr ctx))))
(defn world-viewport-height [ctx] (vp-world-height (world-viewport (gr ctx))))

(defn- mapvals [f m]
  (into {} (for [[k v] m]
             [k (f v)])))

(defn- ->cursors [cursors]
  {:cursors (mapvals (fn [[file hotspot]]
                       (->cursor (str "cursors/" file ".png") hotspot))
                     cursors)})

(defn set-cursor! [{g :context/graphics} cursor-key]
  (g-set-cursor! (safe-get (:cursors g) cursor-key)))

(defcomponent :tx/cursor
  (do! [[_ cursor-key] ctx]
    (set-cursor! ctx cursor-key)
    ctx))

(defn- render-view [{{:keys [batch] :as g} :context/graphics} view-key draw-fn]
  (let [{:keys [viewport unit-scale]} (view-key g)]
    (draw-with! batch
                viewport
                (fn []
                  (with-shape-line-width g
                    unit-scale
                    #(draw-fn (assoc g :unit-scale unit-scale)))))))

(defn render-gui-view
  "render-fn is a function of param 'g', graphics context."
  [ctx render-fn]
  (render-view ctx :gui-view render-fn))

(defn render-world-view
  "render-fn is a function of param 'g', graphics context."
  [ctx render-fn]
  (render-view ctx :world-view render-fn))

(defn- update-viewports! [{g :context/graphics} dim]
  (vp-update! (gui-viewport g) dim :center-camera? true)
  ; Do not center the camera on world-viewport. We set the position there manually.
  (vp-update! (world-viewport g) dim))

(defprotocol DrawItemOnCursor
  (draw-item-on-cursor [g ctx]))

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

(defcomponent :context/graphics
  {:data [:map [:cursors :default-font :views]]
   :let {:keys [views default-font cursors]}}
  (->mk [_ _ctx]
    (map->Graphics
     (let [batch (->sprite-batch)]
       (merge {:batch batch}
              (->shape-drawer batch)
              (->default-font default-font)
              (->views views)
              (->cursors cursors)))))

  (destroy! [[_ {:keys [batch shape-drawer-texture default-font cursors]}]]
    (dispose! batch)
    (dispose! shape-drawer-texture)
    (dispose! default-font)
    (run! dispose! (vals cursors))))
