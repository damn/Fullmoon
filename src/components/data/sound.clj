(ns components.data.sound
  (:require [clojure.string :as str]
            [core.component :refer [defcomponent]]
            [core.context :as ctx]
            [core.data :as data]
            [core.scene2d.actor :as actor]
            [core.scene2d.group :as group]
            [core.scene2d.ui.table :as table]))

(defcomponent :sound {:schema :string})

(defn- ->scrollable-choose-window [ctx rows]
  (ctx/->window ctx {:title "Choose"
                     :modal? true
                     :close-button? true
                     :center? true
                     :close-on-escape? true
                     :rows [[(data/->scroll-pane-cell ctx rows)]]
                     :pack? true}))

(defn- ->play-sound-button [ctx sound-file]
  (ctx/->text-button ctx "play!" #(do (ctx/play-sound! % sound-file) %)))

(declare ->sound-columns)

(defn- open-sounds-window! [ctx table]
  (let [rows (for [sound-file (ctx/all-sound-files ctx)]
               [(ctx/->text-button ctx
                                   (str/replace-first sound-file "sounds/" "")
                                   (fn [{:keys [context/actor] :as ctx}]
                                     (group/clear-children! table)
                                     (table/add-rows! table [(->sound-columns ctx table sound-file)])
                                     (actor/remove! (actor/find-ancestor-window actor))
                                     (actor/pack-ancestor-window! table)
                                     (actor/set-id! table sound-file)
                                     ctx))
                (->play-sound-button ctx sound-file)])]
    (ctx/add-to-stage! ctx (->scrollable-choose-window ctx rows))))

(defn- ->sound-columns [ctx table sound-file]
  [(ctx/->text-button ctx (name sound-file) #(open-sounds-window! % table))
   (->play-sound-button ctx sound-file)])

(defmethod data/->widget :sound [_ sound-file ctx]
  (let [table (ctx/->table ctx {:cell-defaults {:pad 5}})]
    (table/add-rows! table [(if sound-file
                              (->sound-columns ctx table sound-file)
                              [(ctx/->text-button ctx "No sound" #(open-sounds-window! % table))])])
    table))
