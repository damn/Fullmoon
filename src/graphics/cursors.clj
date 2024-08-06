(ns graphics.cursors
  (:require [utils.core :refer [mapvals]]
            [api.disposable :refer [dispose]])
  (:import com.badlogic.gdx.Gdx
           com.badlogic.gdx.graphics.Pixmap))

(def ^:private cursors
  {:cursors/default ["default" 0 0]
   :cursors/black-x ["black_x" 0 0]
   :cursors/denied ["denied" 16 16]
   :cursors/hand-grab ["hand003" 4 16]
   :cursors/hand-before-grab ["hand004" 4 16]
   :cursors/hand-before-grab-gray ["hand004_gray" 4 16]
   :cursors/over-button ["hand002" 0 0]
   :cursors/sandclock ["sandclock" 16 16]
   :cursors/walking ["walking" 16 16]
   :cursors/no-skill-selected ["denied003" 0 0]
   :cursors/use-skill ["pointer004" 0 0]
   :cursors/skill-not-usable ["x007" 0 0]
   :cursors/bag ["bag001" 0 0]
   :cursors/move-window ["move002" 16 16]
   :cursors/princess ["exclamation001" 0 0]
   :cursors/princess-gray ["exclamation001_gray" 0 0]})

(defn- ->cursor [file hotspot-x hotspot-y]
  (let [pixmap (Pixmap. (.internal Gdx/files file))
        cursor (.newCursor Gdx/graphics pixmap hotspot-x hotspot-y)]
    (dispose pixmap)
    cursor))

; TODO dispose all cursors
(defn ->build []
  {:cursors (mapvals (fn [[file x y]]
                       (->cursor (str "cursors/" file ".png") x y))
                     cursors)})
