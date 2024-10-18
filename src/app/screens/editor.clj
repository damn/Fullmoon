(ns app.screens.editor
  (:require [component.property :as property]
            [editor.overview :refer [overview-table]]
            [editor.visui :as editor]
            [gdx.input :refer [key-just-pressed?]]
            [gdx.ui :as ui]
            [gdx.ui.stage-screen :as stage-screen :refer [stage-add!]]
            [gdx.screen :as screen])
  (:import (com.kotcrab.vis.ui.widget.tabbedpane Tab TabbedPane TabbedPaneAdapter)))

(defn- tabs-data []
  (for [property-type (sort (property/types))]
    {:title (:title (property/overview property-type))
     :content (overview-table property-type (fn [property-id]
                                              (stage-add! (editor/property-editor-window property-id))))}))

(defn- ->tab [{:keys [title content savable? closable-by-user?]}]
  (proxy [Tab] [(boolean savable?) (boolean closable-by-user?)]
    (getTabTitle [] title)
    (getContentTable [] content)))

(defn- tabbed-pane [tabs-data]
  (let [main-table (ui/table {:fill-parent? true})
        container (ui/table {})
        tabbed-pane (TabbedPane.)]
    (.addListener tabbed-pane
                  (proxy [TabbedPaneAdapter] []
                    (switchedTab [^Tab tab]
                      (.clearChildren container)
                      (.fill (.expand (.add container (.getContentTable tab)))))))
    (.fillX (.expandX (.add main-table (.getTable tabbed-pane))))
    (.row main-table)
    (.fill (.expand (.add main-table container)))
    (.row main-table)
    (.pad (.left (.add main-table (ui/label "[LIGHT_GRAY]Left-Shift: Back to Main Menu[]"))) (float 10))
    (doseq [tab-data tabs-data]
      (.add tabbed-pane (->tab tab-data)))
    main-table))

(defn screen [->background-image]
  [:screens/property-editor
   (stage-screen/create :actors
                        [(->background-image)
                         (tabbed-pane (tabs-data))
                         (ui/actor {:act (fn []
                                           #_(when (key-just-pressed? :shift-left)
                                             (screen/change! :screens/main-menu)))})])])
