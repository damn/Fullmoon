(ns clojure.ctx
  "## Glossary

  | Name             | Meaning                                        |
  | ---------------  | -----------------------------------------------|
  | `component`      | vector `[k v]` |
  | `system`         | multimethod dispatching on component k |
  | `eid` , `entity` | entity atom                                    |
  | `entity*`        | entity value |
  | `cell`/`cell*`   | Cell of the world grid or inventory  |
  | `g`              | `clojure.ctx.Graphics`                        |
  | `grid`           | `data.grid2d.Grid`                             |
  | `image`          | `clojure.ctx.Image`                          |
  | `position`       | `[x y]` vector                                 |"
  {:metadoc/categories {:app "🖥️ Application"
                        :ctx "📜 Context"
                        :graphics "🖌️ Graphics"
                        :ui "🎛️ UI"
                        :utils "🔧 Utils"
                        :world "🌎 World"}}
  (:require (clojure [gdx :refer :all]
                     [set :as set]
                     [string :as str]
                     [edn :as edn]
                     [math :as math]
                     [pprint :refer [pprint]])
            [clj-commons.pretty.repl :refer [pretty-pst]]
            (malli [core :as m]
                   [error :as me]
                   [generator :as mg]))
  (:load "ctx/effect"
         "ctx/component"
         "ctx/assets"
         "ctx/schema" ; => not part of ctx
         "ctx/graphics"

         "ctx/val_max" ; => not part of ctx -> utils (but then clojure.gdx depends on malli)

         "ctx/ui" ; confusing
         "ctx/properties" ; confusing

         "ctx/operation" ; -> before modifier/stats/effects (not part of ctx)

         "ctx/app" ; w. screens?
         "ctx/world"
         "ctx/doc"))
