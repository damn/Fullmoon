<p align="center">

![core](https://github.com/user-attachments/assets/274f2c3c-0375-4e67-ac5a-196f64744f10)


</p>

# What is core?

* An action-RPG roguelike game
* A GUI-Tool for editing the game itself
* A framework for writing such games
* A modular engine which can be used as a library too

Please note that it is very experimental and work-in-progress at the moment.
I would appreciate very much any feedback!

# Screenshots
<details>
  <summary>Ingame</summary>
<img width="1437" alt="Screenshot 2024-09-11 at 10 59 32 PM" src="https://github.com/user-attachments/assets/19c2a342-0e70-4925-a203-2e8c229e4ea0">

</details>
<details>
  <summary>Editor</summary>
  <img width="1432" alt="Screenshot 2024-09-08 at 11 53 59 PM" src="https://github.com/user-attachments/assets/87c9edc0-5aab-4642-ae4d-f08291ec7970">

</details>

# How to start

```
lein run
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

# [API Docs](https://damn.github.io/core/)

# Code Licensed under MIT License.

# Asset license

The assets used are proprietary and not open source.

* Tilesets by https://winlu.itch.io/
* Creatures, Items, Skill-Icons,FX and other assets by https://www.oryxdesignlab.com
* Cursors from Leonid Deburger https://deburger.itch.io/
