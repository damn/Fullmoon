(ns core.property.data.sound
  (:require [clojure.string :as str]
            [core.ctx.assets :as assets]
            [core.component :refer [defcomponent]]
            [core.ctx.property :as property]
            [core.screens.stage :as stage]
            [gdx.scene2d.actor :as actor]
            [gdx.scene2d.group :as group]
            [gdx.scene2d.ui :as ui]))

(defcomponent :sound {:schema :string})

(defn- ->scrollable-choose-window [ctx rows]
  (ui/->window {:title "Choose"
                :modal? true
                :close-button? true
                :center? true
                :close-on-escape? true
                :rows [[(ui/->scroll-pane-cell ctx rows)]]
                :pack? true}))

(defn- ->play-sound-button [ctx sound-file]
  (ui/->text-button ctx "play!" #(assets/play-sound! % sound-file)))

(declare ->sound-columns)

(defn- open-sounds-window! [ctx table]
  (let [rows (for [sound-file (:sound-files (:context/assets ctx))]
               [(ui/->text-button ctx
                                  (str/replace-first sound-file "sounds/" "")
                                  (fn [{:keys [context/actor] :as ctx}]
                                    (group/clear-children! table)
                                    (ui/add-rows! table [(->sound-columns ctx table sound-file)])
                                    (actor/remove! (actor/find-ancestor-window actor))
                                    (actor/pack-ancestor-window! table)
                                    (actor/set-id! table sound-file)
                                    ctx))
                (->play-sound-button ctx sound-file)])]
    (stage/add-actor! ctx (->scrollable-choose-window ctx rows))))

(defn- ->sound-columns [ctx table sound-file]
  [(ui/->text-button ctx (name sound-file) #(open-sounds-window! % table))
   (->play-sound-button ctx sound-file)])

(defmethod property/->widget :sound [_ sound-file ctx]
  (let [table (ui/->table {:cell-defaults {:pad 5}})]
    (ui/add-rows! table [(if sound-file
                           (->sound-columns ctx table sound-file)
                           [(ui/->text-button ctx "No sound" #(open-sounds-window! % table))])])
    table))
