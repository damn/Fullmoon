The name ctx comes from context and the idea of keeping the whole application state and game world in one data structure.

## Special operators

## Side effects

## Content

The whole game content is stored in `resources/properties.edn` and can be configured in the GUI-editor.

There are 7 property-types:

## 🖥️ App

## 💥 Audiovisuals

## 🐉 Creatures

## ⚔️ Items

## 🚀 Projectiles

## 🪄 Skills

## 🌎 Worlds


## Screenshots

<img width="1437" alt="Screenshot 2024-09-11 at 10 59 32 PM" src="https://github.com/user-attachments/assets/19c2a342-0e70-4925-a203-2e8c229e4ea0">

<details>
  <summary>Context Inspector</summary>

  <img width="1425" alt="Screenshot 2024-09-19 at 10 42 45 PM" src="https://github.com/user-attachments/assets/4819dd7f-93eb-4096-b392-aec8e39c6905">


</details>
<details>
  <summary>Property Editor</summary>
  <img width="1432" alt="Screenshot 2024-09-08 at 11 53 59 PM" src="https://github.com/user-attachments/assets/87c9edc0-5aab-4642-ae4d-f08291ec7970">

</details>

## How to start

```
lein run
```

### Interactive dev-loop

```
lein dev
```

It will start the application and also:
* Starts an NREPL-Server
* On application close (ESC in the main menu), clojure.tools.namespace will do  refresh on any changed files and restart the app.
* On any error the JVM does not have to be restarted, you can fix the error and call `clojure.gdx.dev/restart!`
    * I have bound it on my VIM to F5 with: `nmap <F5> :Eval (do (in-ns 'clojure.gdx.dev)(restart!))`

## [API Docs](https://damn.github.io/clojure.ctx/)

## License

* Code Licensed under MIT License

* The assets used are proprietary and not open source
    * Tilesets by https://winlu.itch.io/
    * Creatures, Items, Skill-Icons,FX and other assets by https://www.oryxdesignlab.com
    * Cursors from Leonid Deburger https://deburger.itch.io/
    * The font exocet is open source
