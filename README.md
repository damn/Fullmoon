# core

# #1 Optimize for programmer happyness

# Screenshots

![foo](screenshots/main.png)

# How to start

Start normally
```
lein run -m app resources/app.edn
```

Start dev loop
```
lein dev
```

# License

iDK about licenses

# How to profile performance

* Use sampler not profiler @ JVIsualVM
* Do not limit max FPS, set to 300+
* Lazy seqs hide evaluation & slower (e.g. main loop filter this, etc) ! use transducers (also hide steps in the into...)
* boxed math ! *unchecked-math* :warn-on-boxed
* in production set *assert* to false? @ cell grid lots of checks add/remove entity
* 2d array faster than grid2d? But not 'immutable'!
* entity valAt → move accessors into protocol fns e.g. position and use .position entity or only non-ns keyword
* lightmap calculations

# Glossary

table 'ctx' 'eid' entity*, entity, body, ...

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
