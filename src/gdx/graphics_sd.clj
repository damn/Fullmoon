(in-ns 'gdx.graphics)

(declare ^:private sd
         ^:private sd-texture)

(defn- sd-color [sd color]
  (sd/set-color sd (munge-color color)))

(defn draw-ellipse [position radius-x radius-y color]
  (sd-color sd color)
  (sd/ellipse sd position radius-x radius-y))

(defn draw-filled-ellipse [position radius-x radius-y color]
  (sd-color sd color)
  (sd/filled-ellipse sd position radius-x radius-y))

(defn draw-circle [position radius color]
  (sd-color sd color)
  (sd/circle sd position radius))

(defn draw-filled-circle [position radius color]
  (sd-color sd color)
  (sd/filled-circle sd position radius))

(defn draw-arc [center radius start-angle degree color]
  (sd-color sd color)
  (sd/arc sd center radius start-angle degree))

(defn draw-sector [center radius start-angle degree color]
  (sd-color sd color)
  (sd/sector sd center radius start-angle degree))

(defn draw-rectangle [x y w h color]
  (sd-color sd color)
  (sd/rectangle sd x y w h))

(defn draw-filled-rectangle [x y w h color]
  (sd-color sd color)
  (sd/filled-rectangle sd x y w h))

(defn draw-line [start end color]
  (sd-color sd color)
  (sd/line sd start end))

(defn draw-grid [leftx bottomy gridw gridh cellw cellh color]
  (sd-color sd color)
  (sd/grid sd leftx bottomy gridw gridh cellw cellh))

(defn with-shape-line-width [width draw-fn]
  (sd/with-line-width sd width draw-fn))
