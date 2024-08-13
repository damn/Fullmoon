(ns clj.gdx.input.buttons
  (:require [utils.reflect :refer [bind-roots]]))

(declare back
         forward
         left
         middle
         right)

(bind-roots "com.badlogic.gdx.Input$Buttons" 'int "clj.gdx.input.buttons")
