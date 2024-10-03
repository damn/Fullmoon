
<img width="1440" alt="Screenshot 2024-10-03 at 12 08 30 PM" src="https://github.com/user-attachments/assets/6780f0c4-0729-46ca-b254-a1816af2d6c8">

## How to start

```
lein run
```

### Interactive dev-loop

```
lein dev
```

<details>
  <summary>Dev-loop contains:</summary>

* NREPL-Server
* On application close (ESC in the main menu), clojure.tools.namespace will do  refresh on any changed files and restart the app.
* On any error the JVM does not have to be restarted, you can fix the error and call `clojure.gdx.dev/restart!`
    * I have bound it on my VIM to F5 with: `nmap <F5> :Eval (do (in-ns 'clojure.gdx.dev)(restart!))`

</details>

## [API Docs](https://damn.github.io/clojure.ctx/)

## License

* Code Licensed under MIT License

* The assets used are proprietary and not open source
    * Tilesets by https://winlu.itch.io/
    * Creatures, Items, Skill-Icons,FX and other assets by https://www.oryxdesignlab.com
    * Cursors from Leonid Deburger https://deburger.itch.io/
    * The font exocet is open source
