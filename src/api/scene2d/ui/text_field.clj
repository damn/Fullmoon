(ns api.scene2d.ui.text-field
  (:import com.kotcrab.vis.ui.widget.VisTextField))

(defn text [^VisTextField text-field]
  (.getText text-field))
