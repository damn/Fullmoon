# core

RPG Maker&Engine for Clojure.

It uses an extremely innovating new component system, where components are just [k v] clojure vectors.

Side effects in the game are just components like `[:tx/foo param]` named 'tx=transaction' similar to the datomic structure.

The whole game state is stored in one atom: `app/state` and entities are again atoms inside the main atom.

# Screenshots

<img width="1680" alt="screenshot" src="https://github.com/user-attachments/assets/1c7451d0-57f0-48c9-bee3-8eedf332910f">

<img width="1432" alt="Screenshot 2024-09-08 at 11 53 59 PM" src="https://github.com/user-attachments/assets/aee42c1d-4b34-4efc-b40a-21fd0fd9e3c9">

# How to start developing

* Starts an NREPL-Server
* On application close (ESC in the main menu), clojure.tools.namespace will do a refresh on any changed files and restart the app.
```
lein dev
```

# Code Licensed under MIT License.

# Asset license

The assets used are proprietary and not open source.

* Tilesets by https://winlu.itch.io/
* Creatures, Items, Skill-Icons,FX and other assets by https://www.oryxdesignlab.com
* Cursors from Leonid Deburger https://deburger.itch.io/
