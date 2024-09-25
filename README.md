# clojure.world

A programming language for creating worlds based on [clojure](https://clojure.org/) and [libgdx](https://libgdx.com/).

![moon](https://github.com/user-attachments/assets/8bf2227d-74c1-4eb5-85a5-4df926f4ea1b)

# Screenshots

<details>
  <summary>World</summary>
<img width="1437" alt="Screenshot 2024-09-11 at 10 59 32 PM" src="https://github.com/user-attachments/assets/19c2a342-0e70-4925-a203-2e8c229e4ea0">

</details>

<details>
  <summary>Context Inspector</summary>

  <img width="1425" alt="Screenshot 2024-09-19 at 10 42 45 PM" src="https://github.com/user-attachments/assets/4819dd7f-93eb-4096-b392-aec8e39c6905">


</details>
<details>
  <summary>Property Editor</summary>
  <img width="1432" alt="Screenshot 2024-09-08 at 11 53 59 PM" src="https://github.com/user-attachments/assets/87c9edc0-5aab-4642-ae4d-f08291ec7970">

</details>

# How to start

```
lein run
```

## Interactive dev-loop

```
lein dev
```

It will start the application and also:
* Starts an NREPL-Server
* On application close (ESC in the main menu), clojure.tools.namespace will do  refresh on any changed files and restart the app.
* On any error the JVM does not have to be restarted, you can fix the error and call `dev.interactive/restart!`
    * I have bound it on my VIM to F5 with: `nmap <F5> :Eval (do (in-ns 'dev.interactive)(restart!))`

# [API Docs](https://damn.github.io/clojure.world/)


# License

* Code Licensed under MIT License

* The assets used are proprietary and not open source
    * Tilesets by https://winlu.itch.io/
    * Creatures, Items, Skill-Icons,FX and other assets by https://www.oryxdesignlab.com
    * Cursors from Leonid Deburger https://deburger.itch.io/
    * The font exocet is open source
