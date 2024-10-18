## Instructions

* Add (:gen-class) to core.app
* `lein uberjar`
* `java -jar target/uberjar/cdq_3.jar`

## works in game folder!

## out of game folder

Assets not working => see cdq jar file search zipstuff
```
(.list (.classpath Gdx/files "."))
=> Cannot list classpath directory
```

then I could not pass 'resources/' anymore because it is available in classpath anyway
