(ns mapgen.movement-property
  (:require [gdl.maps.tiled :as tiled]))

; TODO performance bottleneck -> every time getting same layers
; takes 600 ms to read movement-properties

(defn- tile-movement-property [tiled-map layer position]
  (let [value (tiled/property-value tiled-map layer position :movement)]
    (assert (not= value :undefined)
            (str "Value for :movement at position "
                 position  " / mapeditor inverted position: " [(position 0)
                                                               (- (dec (tiled/height tiled-map))
                                                                  (position 1))]
                 " and layer " (tiled/layer-name layer) " is undefined."))
    (when-not (= :no-cell value)
      value)))

(defn- movement-property-layers [tiled-map]
  (filter #(tiled/get-property % :movement-properties)
          (reverse
           (tiled/layers tiled-map))))

(defn movement-properties [tiled-map position]
  (for [layer (movement-property-layers tiled-map)]
    [(tiled/layer-name layer)
     (tile-movement-property tiled-map layer position)]))

(defn movement-property [tiled-map position]
  (or (->> tiled-map
           movement-property-layers
           (some #(tile-movement-property tiled-map % position)))
      "none"))
