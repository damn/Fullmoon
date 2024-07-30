(ns context.cursor
  (:require [utils.core :refer [safe-get mapvals]]
            [core.component :as component]
            [api.context :as ctx]
            [api.tx :refer [transact!]]))

(extend-type api.context.Context
  api.context/Cursor
  (set-cursork! [{:keys [context/cursor] :as ctx} cursor-key]
    (ctx/set-cursor! ctx (safe-get cursor cursor-key))))

(def ^:private cursors
  {:cursors/default ["default" 0 0]
   :cursors/black-x ["black_x" 0 0]
   :cursors/denied ["denied" 16 16]
   :cursors/hand-grab ["hand003" 4 16]
   :cursors/hand-before-grab ["hand004" 4 16]
   :cursors/hand-before-grab-gray ["hand004_gray" 4 16]
   :cursors/over-button ["hand002" 0 0]
   :cursors/sandclock ["sandclock" 16 16]
   :cursors/walking ["walking" 16 16]
   :cursors/no-skill-selected ["denied003" 0 0]
   :cursors/use-skill ["pointer004" 0 0]
   :cursors/skill-not-usable ["x007" 0 0]
   :cursors/bag ["bag001" 0 0]
   :cursors/move-window ["move002" 16 16]
   :cursors/princess ["exclamation001" 0 0]
   :cursors/princess-gray ["exclamation001_gray" 0 0]})

; TODO dispose all cursors , implement gdl.disposable
; => move to gdl ....
(component/def :context/cursor {}
  _
  (ctx/create [_ ctx]
    (let [cursors (mapvals (fn [[file x y]]
                             (ctx/->cursor ctx (str "cursors/" file ".png") x y))
                           cursors)]
      (ctx/set-cursor! ctx (:cursors/default cursors))
      cursors)))

(defmethod transact! :tx/cursor [[_ cursor-key] ctx]
  (ctx/set-cursork! ctx cursor-key)
  nil)
