(ns cdq.entity.body
  (:require [core.component :as component]
            [gdl.graphics :as g]
            [gdl.graphics.color :as color]
            [cdq.api.entity :as entity]
            [cdq.attributes :as attr]))

; setting a min-size for colliding bodies so movement can set a max-speed for not
; skipping bodies at too fast movement
(def min-solid-body-size 0.4)

(defn- draw-bounds [g {[x y] :left-bottom :keys [width height solid?]}]
  (g/draw-rectangle g x y width height (if solid? color/white color/gray)))

(def show-body-bounds false)

(defrecord Body [width
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
(component/def :width  {:widget :label :schema pos?}) ; TODO make px
(component/def :height {:widget :label :schema pos?}) ; TODO make px
(component/def :solid? {:widget :label :schema boolean?})

; TODO body assert >+ min body size?
(component/def :entity/body (attr/map-attribute :width :height :solid?)
  body
  (entity/create-component [_ {:keys [entity/position]
                               [x y] :entity/position
                               {:keys [width
                                       height
                                       solid?
                                       rotation-angle
                                       rotate-in-movement-direction?]} :entity/body} _ctx]
    (assert position)
    (assert (and width height
                 (>= width  (if solid? min-solid-body-size 0))
                 (>= height (if solid? min-solid-body-size 0))
                 (boolean? solid?)
                 (or (nil? rotation-angle)
                     (<= 0 rotation-angle 360))))
    (map->Body
     {:left-bottom [(- x (/ width  2))
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

  (entity/render-debug [_ _entity* g _ctx]
    (when show-body-bounds
      (draw-bounds g body))))
