(ns gdl.scene2d.ui.table
  (:import com.kotcrab.vis.ui.widget.Separator))

(defprotocol Table
  (cells [_])
  (add-rows! [_ rows] "rows is a seq of seqs of columns.
                      Elements are actors or a map of
                      {:actor :expand? :bottom?  :colspan int :pad :pad-bottom}. Only :actor is required.")

  (add! [_ actor] "Adds a new cell to the table with the specified actor."))

(defn ->horizontal-separator-cell [colspan]
  {:actor (Separator. "default")
   :pad-top 2
   :pad-bottom 2
   :colspan colspan
   :fill-x? true
   :expand-x? true})

(defn ->vertical-separator-cell []
  {:actor (Separator. "vertical")
   :pad-top 2
   :pad-bottom 2
   :fill-y? true
   :expand-y? true})
