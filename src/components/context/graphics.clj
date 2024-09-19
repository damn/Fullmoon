(ns components.context.graphics
  (:require [core.component :refer [defcomponent] :as component]
            [core.graphics :as g]
            (components.graphics cursors
                                 shape-drawer
                                 text
                                 views))
  (:import com.badlogic.gdx.graphics.Color
           (com.badlogic.gdx.graphics.g2d Batch SpriteBatch)
           com.badlogic.gdx.utils.Disposable
           com.badlogic.gdx.utils.viewport.Viewport))

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
     (let [batch (SpriteBatch.)]
       (merge {:batch batch}
              (components.graphics.shape-drawer/->build batch)
              (components.graphics.text/->build default-font)
              (components.graphics.views/->build views)
              (components.graphics.cursors/->build cursors)))))

  (component/destroy! [[_ {:keys [batch shape-drawer-texture default-font cursors]}]]
    (.dispose ^Disposable batch)
    (.dispose ^Disposable shape-drawer-texture)
    (.dispose ^Disposable default-font)
    (run! Disposable/.dispose (vals cursors))))

(defn- render-view [{{:keys [^Batch batch] :as g} :context/graphics}
                    view-key
                    draw-fn]
  (let [{:keys [^Viewport viewport unit-scale]} (view-key g)]
    (.setColor batch Color/WHITE) ; fix scene2d.ui.tooltip flickering
    (.setProjectionMatrix batch (.combined (.getCamera viewport)))
    (.begin batch)
    (g/with-shape-line-width g
                             unit-scale
                             #(draw-fn (assoc g :unit-scale unit-scale)))
    (.end batch)))

(extend-type core.context.Context
  core.context/Graphics
  (render-gui-view   [ctx render-fn] (render-view ctx :gui-view   render-fn))
  (render-world-view [ctx render-fn] (render-view ctx :world-view render-fn)))
