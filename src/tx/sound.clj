(ns tx.sound
  (:require [core.component :refer [defcomponent]]
            [api.context :refer [play-sound!]]
            [api.tx :refer [transact!]]
            [core.data :as data]))

; how can I use tx as components/? schema check ? validate before do ? ?? tests ?? context ?
; edit .... select from tx .....
; like new game button tx/start-game
; .... ? where are tx used ??? there just make editable pass data (which contexts are available?)
; check transact-all! to see .... for example princess saved sound & modal text ...
(defcomponent :tx/sound data/sound
  (transact! [[_ file] ctx]
    (play-sound! ctx file)
    nil))
