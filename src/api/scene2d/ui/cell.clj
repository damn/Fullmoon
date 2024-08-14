(ns api.scene2d.ui.cell
  (:import com.badlogic.gdx.scenes.scene2d.ui.Cell))

(defn set-actor! [^Cell cell actor]
  (.setActor cell actor))
