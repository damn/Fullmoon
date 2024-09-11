
<img width="1437" alt="Screenshot 2024-09-11 at 10 59 32 PM" src="https://github.com/user-attachments/assets/9e47fb6e-709b-49a1-9fe5-0cc1e936b0d1">


# How to start

```
lein run -m app "resources/properties.edn"
```

# How to start interactive dev-loop

```
lein dev
```

It will start the application and also:
* Starts an NREPL-Server
* On application close (ESC in the main menu), clojure.tools.namespace will do  refresh on any changed files and restart the app.
* On any error the JVM does not have to be restarted, you can fix the error and call `dev-loop/restart!` I have bound it on my VIM to F5 with:
  `nmap <F5> :Eval (do (in-ns 'dev-loop)(restart!))`

# Code Licensed under MIT License.

# Asset license

The assets used are proprietary and not open source.

* Tilesets by https://winlu.itch.io/
* Creatures, Items, Skill-Icons,FX and other assets by https://www.oryxdesignlab.com
* Cursors from Leonid Deburger https://deburger.itch.io/
