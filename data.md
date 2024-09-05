Also move bookmarks, dropbox, everything into github
just share all the stuff

Main:
* stats
* core.stat similar to modifiers just not sorted
* target-all/target-entity lots of TODOs
* mapgen/cavgen most TODO's

* components.entity.temp-modifier

* potential-fields
* :entity/projectile-collision
* shout
* damage/armor-pierce
* components.entity-state.active-skill ?
* player idle todos/else-cases/etc.
* item on cursor place item on water/out-of-sight (ticket is there)
* src/components/graphics/views.clj clamping TODOs
* player doesn;t need aggro-range/reaction-time
* order files by # comments/TODOs?
* armor at damage no audiovisual
* creature spawn comments
* properties item comments
* properties projectilev ery rawy

* debug window -> ctx info component?!
* line of sight !! world-view .... also explored tiles foobar ...
* untested create-double-ray-endpositions


* Here TODO txts
* Dropbox ramblings
* Review code manually (checklist, move values out, clj-kondo also)
e.g.
src/components/entity/temp_modifier.clj
context/world passed vampire value,... (checklist no values passed, components, etc. )
dead-code minimap/skill-window/replay-mode. (

* Also : use this-component-key and don't access data directly? e.g. :context/properties everywhere?

* components clear what they do if added to an entity (faction? )
=> actually faction doesn't do anything but npc-moving & potential field does .... interesting
that part needs to be clearer.

move /dev into dev/ and rest into core
lose gdx ?

* app-values-tree (colors, potential-field cache, sizes, etc. global data )
* namespace tree fix dependencies

=> all DONE or in gh-issues

=> also untested stuff like contentfields,path-ray-blocked? ...

=> overview ? whats going on , what is unfinished, what is untested, ... ?
