(ns components.properties.app
  (:require [core.component :as component :refer [defcomponent]]
            [core.context :as ctx]))

(defcomponent :app/lwjgl3 {:data :some})
(defcomponent :app/context {:data :some})

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
