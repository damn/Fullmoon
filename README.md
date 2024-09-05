# core

# Screenshots

![foo](screenshots/main.png)
![foo](screenshots/caves.png)
![foo](screenshots/editor.png)
![foo](screenshots/inventory.png)
![foo](screenshots/vampire.png)

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
