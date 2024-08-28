(ns components.properties.app
  (:require [core.component :as component :refer [defcomponent]]
            [core.context :as ctx]))

(defcomponent :fps {:data :nat-int})
(defcomponent :full-screen? {:data :boolean})
(defcomponent :width {:data :nat-int})
(defcomponent :height {:data :nat-int})
(defcomponent :title {:data :string})

(defcomponent :app/lwjgl3 {:data [:map [:fps :full-screen? :width :height :title]]})
(defcomponent :app/context {:data [:components-ns :context]})

(defcomponent :properties/app
  (component/create [_ _ctx]
    {:id-namespace "app"
     :schema [[:property/id [:qualified-keyword {:namespace :app}]]
              [:app/lwjgl3
               :app/context]]
     :edn-file-sort-order -1
     :overview {:title "Apps"
                :columns 10
                :image/dimensions [96 96]}}))
