![moon_background](https://github.com/user-attachments/assets/02b007a9-cf1d-4ce4-92ae-e4299684b99c)

Based on [clojure](https://clojure.org/) & [libgdx](https://libgdx.com/).

Featuring a property editor for creatures, skills, items, effects, modifiers, and audiovisuals..

## Screenshots

<details>
  <summary>Ingame</summary>
  <img width="1440" alt="Screenshot 2024-10-03 at 12 08 30 PM" src="https://github.com/user-attachments/assets/6780f0c4-0729-46ca-b254-a1816af2d6c8">
</details>

<details>
  <summary>Editor</summary>
<img width="750" alt="reddragoneditor" src="https://github.com/user-attachments/assets/62b91a90-784e-4948-a89f-7d18fefce142">


</details>

## Levels

Levels can be created with [tiled](https://www.mapeditor.org/) or procedurally or mixed with the use of modules. There is one example world for each approach.

<details>
  <summary>Screenshot</summary>
  <img width="1440" alt="Screenshot 2024-10-07 at 6 22 54 PM" src="https://github.com/user-attachments/assets/a59d276b-ab6b-4a28-a392-5aa62823d6f8">

</details>

## How to start

You need to have [leiningen](https://leiningen.org/) installed.

```
./dev
```

<details>
  <summary>Dev-loop contains:</summary>

* NREPL-Server
* On application close (ESC in the main menu), clojure.tools.namespace will do  refresh on any changed files and restart the app.
* On any error the JVM does not have to be restarted, you can fix the error and call `gdx.dev/restart!`
    * I have bound it on my VIM to F5 with: `nmap <F5> :Eval (do (in-ns 'gdx.dev)(restart!))`

</details>

## [API Docs](https://damn.github.io/Fullmoon/)

## License

* Code Licensed under MIT License

* The assets used are proprietary and not open source
    * Tilesets by https://winlu.itch.io/
    * Creatures, Items, Skill-Icons,FX and other assets by https://www.oryxdesignlab.com
    * Cursors from Leonid Deburger https://deburger.itch.io/
    * The font exocet is open source

