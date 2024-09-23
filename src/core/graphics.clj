(ns core.graphics
  (:import com.badlogic.gdx.graphics.Color))

(defn ->color
  ([r g b]
   (->color r g b 1))
  ([r g b a]
   (Color. (float r) (float g) (float b) (float a))))

(defrecord Graphics [batch
                     shape-drawer
                     gui-view
                     world-view
                     default-font
                     unit-scale
                     cursors])

(defprotocol WorldView
  (pixels->world-units [_ pixels])
  (world-unit-scale [_]))

(defprotocol ShapeDrawer
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

(defprotocol TextDrawer
  (draw-text [_ {:keys [x y text font h-align up? scale]}]
             "font, h-align, up? and scale are optional.
             h-align one of: :center, :left, :right. Default :center.
             up? renders the font over y, otherwise under.
             scale will multiply the drawn text size with the scale."))

(defprotocol Image
  (draw-image [_ image position])
  (draw-centered-image [_ image position])
  (draw-rotated-centered-image [_ image rotation position]))
