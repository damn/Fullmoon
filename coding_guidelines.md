# Coding Guidlines

* Greppable => don't use #: keyword for entity/world? & global used names grep if unique first before introducing.

Code checklist:
* Hardcoded values out
* Component ns dependencies
* use this-component-key and don't access data directly? e.g. :context/properties everywhere?
    (even entity/faction instead of kw, so can change where it is ... part of creature or whatev )
* greppability ( no #:entity / #:world ?? )
=> also untested stuff like contentfields,path-ray-blocked? ...
* components clear what they do if added to an entity (faction? - where used grep & document? )
=> actually faction doesn't do anything but npc-moving & potential field does .... interesting
that part needs to be clearer.
* app-values-tree (colors, potential-field cache, sizes, etc. global data )
* namespace tree fix dependencies
