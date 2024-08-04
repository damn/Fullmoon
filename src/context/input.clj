(ns context.input
  (:require api.context
            api.input.keys
            api.input.buttons
            [app.libgdx.utils.reflect :refer [bind-roots]])
  (:import (com.badlogic.gdx Gdx Input$Buttons Input$Keys)))

(extend-type api.context.Context
  api.context/Input
  (button-pressed?      [_ button] (.isButtonPressed     Gdx/input button))
  (button-just-pressed? [_ button] (.isButtonJustPressed Gdx/input button))

  (key-pressed?      [_ k] (.isKeyPressed     Gdx/input k))
  (key-just-pressed? [_ k] (.isKeyJustPressed Gdx/input k)))

; TODO FIXME with namespace refresh and using the input.keys they can be not bound yet
; -> namespace dependency tree not working with bind-roots ? do it different?
; for example creating them manually with the right key code in api.input.*
; -> just make keywords!
(bind-roots "com.badlogic.gdx.Input$Keys"    'int "api.input.keys")
(bind-roots "com.badlogic.gdx.Input$Buttons" 'int "api.input.buttons")
