# How to profile performance

* Use sampler not profiler @ JVIsualVM
* Do not limit max FPS, set to 300+
    (also swapbuffer taking 50% of CPU time - see glfw docs, maybe vsync is on? )
    https://www.reddit.com/r/opengl/comments/zerwxm/glfwswapbuffers_taking_almost_60_my_programs_cpu/
* Lazy seqs hide evaluation & slower (e.g. main loop filter this, etc) ! use transducers (also hide steps in the into...)
* boxed math ! *unchecked-math* :warn-on-boxed
* in production set *assert* to false? @ cell grid lots of checks add/remove entity
* 2d array faster than grid2d? But not 'immutable'!
* entity valAt → move accessors into protocol fns e.g. position and use .position entity or only non-ns keyword
* lightmap calculations

* Switch Texture @ shape-drawer => use common entity texture white pixel, not create separate!

```clojure
 (gdx.app/post-runnable #(.setVSync com.badlogic.gdx.Gdx/graphics false))
```

https://libgdx.com/wiki/graphics/profiling
