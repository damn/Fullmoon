(ns tx.sound
  (:require [core.component :as component]
            [api.context :refer [play-sound!]]
            [api.tx :refer [transact!]]
            [core.data :as attr]))

; how can I use tx as components/? schema check ? validate before do ? ?? tests ?? context ?
; edit .... select from tx .....
; like new game button tx/start-game
; .... ? where are tx used ??? there just make editable pass data (which contexts are available?)
; check transact-all! to see .... for example princess saved sound & modal text ...
(component/def :tx/sound attr/sound
  file
  (transact! [_ ctx]
    (play-sound! ctx file)
    nil))
