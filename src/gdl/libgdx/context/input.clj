(ns ^:no-doc gdl.libgdx.context.input
  (:require gdl.context
            gdl.input.keys
            gdl.input.buttons
            [gdl.libgdx.utils.reflect :refer [bind-roots]])
  (:import (com.badlogic.gdx Gdx Input$Buttons Input$Keys)))

(extend-type gdl.context.Context
  gdl.context/Input
  (button-pressed?      [_ button] (.isButtonPressed     Gdx/input button))
  (button-just-pressed? [_ button] (.isButtonJustPressed Gdx/input button))

  (key-pressed?      [_ k] (.isKeyPressed     Gdx/input k))
  (key-just-pressed? [_ k] (.isKeyJustPressed Gdx/input k)))

; TODO FIXME with namespace refresh and using the input.keys they can be not bound yet
; -> namespace dependency tree not working with bind-roots ? do it different?
; for example creating them manually with the right key code in gdl.input.*
; -> just make keywords!
(bind-roots "com.badlogic.gdx.Input$Keys"    'int "gdl.input.keys")
(bind-roots "com.badlogic.gdx.Input$Buttons" 'int "gdl.input.buttons")
