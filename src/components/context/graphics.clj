(ns components.context.graphics
  (:require [gdx.graphics.camera :as camera]
            [gdx.graphics.color :as color]
            [gdx.graphics.g2d :as g2d]
            [gdx.graphics.g2d.batch :as batch]
            [gdx.utils.disposable :refer [dispose]]
            [gdx.utils.viewport.viewport :as viewport]
            [core.component :refer [defcomponent] :as component]
            [core.graphics :as g]
            (components.graphics cursors
                                 shape-drawer
                                 text
                                 views)))

(defcomponent :data/graphics
  {:widget :map
   :schema [:map {:closed true}
            [:cursors :some]
            [:default-font [:map {:closed true}
                            [:file :string]
                            [:quality-scaling pos-int?]
                            [:size pos-int?]]]
            [:views [:map {:closed true}
                     [:gui-view [:map {:closed true}
                                 [:world-width pos-int?]
                                 [:world-height pos-int?]]]
                     [:world-view [:map {:closed true}
                                   [:tile-size pos-int?]
                                   [:world-width pos-int?]
                                   [:world-height pos-int?]]]]]]})

(defcomponent :world-width {:data :pos-int})
(defcomponent :world-height {:data :pos-int})
(defcomponent :tile-size {:data :pos-int})
(defcomponent :world-view {:data [:map [:tile-size :world-width :world-height]]})
(defcomponent :gui-view {:data [:map [:world-width :world-height]]})

(defcomponent :views {:data [:map [:gui-view :world-view]]})

(defcomponent :file {:data :string})
(defcomponent :quality-scaling {:data :pos-int})
(defcomponent :size {:data :pos-int})

(defcomponent :default-font {:data [:map [:file :quality-scaling :size]]})
(defcomponent :cursors {:data :some})

(defcomponent :context/graphics
  {:data [:map [:cursors :default-font :views]]
   :let {:keys [views default-font cursors]}}
  (component/create [_ _ctx]
    (core.graphics/map->Graphics
     (let [batch (g2d/->sprite-batch)]
       (merge {:batch batch}
              (components.graphics.shape-drawer/->build batch)
              (components.graphics.text/->build default-font)
              (components.graphics.views/->build views)
              (components.graphics.cursors/->build cursors)))))

  (component/destroy [[_ {:keys [batch shape-drawer-texture default-font cursors]}]]
    (dispose batch)
    (dispose shape-drawer-texture)
    (dispose default-font)
    (run! dispose (vals cursors))))

(defn- render-view [{{:keys [batch] :as g} :context/graphics}
                    view-key
                    draw-fn]
  (let [{:keys [viewport unit-scale]} (view-key g)]
    (batch/set-color batch color/white) ; fix scene2d.ui.tooltip flickering
    (batch/set-projection-matrix batch (camera/combined (viewport/camera viewport)))
    (batch/begin batch)
    (g/with-shape-line-width g
                             unit-scale
                             #(draw-fn (assoc g :unit-scale unit-scale)))
    (batch/end batch)))

(extend-type core.context.Context
  core.context/Graphics
  (render-gui-view   [ctx render-fn] (render-view ctx :gui-view   render-fn))
  (render-world-view [ctx render-fn] (render-view ctx :world-view render-fn)))
