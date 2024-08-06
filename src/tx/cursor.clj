(ns tx.cursor
  (:require [api.context :as ctx]
            [api.tx :refer [transact!]]))

(defmethod transact! :tx.context.cursor/set [[_ cursor-key] ctx]
  (ctx/set-cursor! ctx cursor-key)
  nil)
