
<img width="1440" alt="Screenshot 2024-10-03 at 12 08 30 PM" src="https://github.com/user-attachments/assets/6780f0c4-0729-46ca-b254-a1816af2d6c8">

## Worlds

<img width="1440" alt="worldeditor" src="https://github.com/user-attachments/assets/43f38c80-b467-4f4c-ac36-73a5b76f88cf">

## Skills & Effects

<img width="1440" alt="skilleditor" src="https://github.com/user-attachments/assets/daf5e3b4-ab4b-4226-9e65-cd7966b33336">
<img width="701" alt="effecteditor" src="https://github.com/user-attachments/assets/e300fb1b-9254-463c-92c3-6f3c12244928">

# Projectiles

<img width="731" alt="projectileeditor" src="https://github.com/user-attachments/assets/7e404f1f-f866-402f-a44c-d4498a28ff18">

# Items & Modifiers

<img width="1440" alt="itemeditormodifier" src="https://github.com/user-attachments/assets/ec8a96ab-a3d5-45a5-994b-c5d01d3f99a0">

# Creatures

<img width="1438" alt="creatureeditor" src="https://github.com/user-attachments/assets/bbcb1d3e-983a-463e-9795-a081fe160511">

## Audiovisuals

<img width="408" alt="audiovisualseditor" src="https://github.com/user-attachments/assets/183a916c-a49b-45e1-b94e-5fd6e751fc54">

## App Settings Editor

<img width="901" alt="appeditor" src="https://github.com/user-attachments/assets/dfba65e1-fc4b-404d-874d-6dfd1838a7a3">


## How to start

You need to have [leiningen](https://leiningen.org/) installed.

```
./dev
```

<details>
  <summary>Dev-loop contains:</summary>

* NREPL-Server
* On application close (ESC in the main menu), clojure.tools.namespace will do  refresh on any changed files and restart the app.
* On any error the JVM does not have to be restarted, you can fix the error and call `clojure.gdx.dev/restart!`
    * I have bound it on my VIM to F5 with: `nmap <F5> :Eval (do (in-ns 'clojure.gdx.dev)(restart!))`

</details>

## License

* Code Licensed under MIT License

* The assets used are proprietary and not open source
    * Tilesets by https://winlu.itch.io/
    * Creatures, Items, Skill-Icons,FX and other assets by https://www.oryxdesignlab.com
    * Cursors from Leonid Deburger https://deburger.itch.io/
    * The font exocet is open source
