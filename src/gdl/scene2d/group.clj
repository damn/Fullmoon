(ns gdl.scene2d.group)

(defprotocol Group
  (children [_] "Returns an ordered list of child actors in this group.")
  (clear-children! [_]
                  "Removes all actors from this group and unfocuses them.")
  (find-actor-with-id [_ id])
  (add-actor! [_ actor]
              "Adds an actor as a child of this group, removing it from its previous parent. If the actor is already a child of this group, no changes are made."))
