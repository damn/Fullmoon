(ns entity.body
  (:require [math.vector :as v]
            [utils.core :refer [->tile]]
            [core.component :refer [defcomponent]]
            [core.data :as data]
            [api.graphics :as g]
            [api.graphics.color :as color]
            [api.entity :as entity]))

; setting a min-size for colliding bodies so movement can set a max-speed for not
; skipping bodies at too fast movement
(def min-solid-body-size 0.4)

(def ^:private ^:debug show-body-bounds false)

(defn- draw-bounds [g {[x y] :left-bottom :keys [width height solid?]}]
  (when show-body-bounds
    (g/draw-rectangle g x y width height (if solid? color/white color/gray))))

(defrecord Body [position
                 width
                 height
                 half-width
                 half-height
                 radius
                 solid?
                 rotation-angle
                 rotate-in-movement-direction?
                 touched-cells
                 occupied-cells])

; TODO how 2 do default values,its not default-values , its non-optional attributes !
; similar to components nested-map
;:default-value {:width 0.5 :height 0.5 :solid? true}
; TODO label == not editable
(defcomponent :width  {:widget :label :schema pos?}) ; TODO make px
(defcomponent :height {:widget :label :schema pos?}) ; TODO make px
(defcomponent :solid? {:widget :label :schema boolean?})

; TODO body assert >+ min body size @ properties !
(defcomponent :entity/body (data/map-attribute :width :height :solid?)
  (entity/create-component [[_
                             {[x y] :position
                              :keys [position
                                     width
                                     height
                                     solid?
                                     rotation-angle
                                     rotate-in-movement-direction?]}]
                            _entity*
                            _ctx]
    (assert (and position
                 width
                 height
                 (>= width  (if solid? min-solid-body-size 0))
                 (>= height (if solid? min-solid-body-size 0))
                 (or (nil? solid?)
                     (boolean? solid?))
                 (or (nil? rotation-angle)
                     (<= 0 rotation-angle 360))))
    (map->Body
     {:position position ; center ?
      :left-bottom [(- x (/ width  2))
                    (- y (/ height 2))]
      :width  (float width)
      :height (float height)
      :half-width  (float (/ width  2))
      :half-height (float (/ height 2))
      :radius (float (max (/ width  2)
                          (/ height 2)))
      :solid? solid?
      :rotation-angle (or rotation-angle 0)
      :rotate-in-movement-direction? rotate-in-movement-direction?}))

  (entity/create [_ {:keys [entity/id]} ctx]
    [[:tx/add-to-world id]])

  (entity/destroy [_ {:keys [entity/id]} ctx]
    [[:tx/remove-from-world id]])

  (entity/render-debug [[_ body] _entity* g _ctx]
    (draw-bounds g body)))

(extend-type api.entity.Entity
  entity/Body
  (position [entity*]
    (:position (:entity/body entity*)))

  (tile [entity*]
    (->tile (entity/position entity*)))

  (direction [entity* other-entity*]
    (v/direction (entity/position entity*) (entity/position other-entity*))))

; TODO maybe 'distance' ? w. body bounds
; TODO also mouseover-circle should be exactly collision circle or make it rect.
