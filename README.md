# DonutTweks

Fabric-Mod für **Minecraft 1.21.11 bis 26.3** von **Xmod_devX**.

Dieses Repository ist das Grundgerüst: Multi-Version-Build über [Stonecutter](https://stonecutter.kikugie.dev),
ein Beispiel-Item, Client-/Server-Entrypoints und Mixin-Konfigurationen.

## Unterstützte Versionen

| Minecraft | Fabric API | Java | Build |
|-----------|------------|------|-------|
| 1.21.11   | 0.141.6+1.21.11 | 21 | eigenes Jar |
| 26.1.2    | 0.155.3+26.1.2  | 25 | eigenes Jar |
| 26.2      | 0.161.0+26.2    | 25 | eigenes Jar |
| 26.3      | 0.166.2+26.3    | 25 | eigenes Jar |

Jede Version bekommt ein eigenes Jar mit passender `fabric.mod.json` (`minecraft`-Range, Java-Version,
Loader-Version). Die Versionen werden zentral in `stonecutter.properties.toml` gepflegt.

## Voraussetzungen

- Windows/macOS/Linux mit Internetzugang (erster Build lädt Gradle, Loom, Minecraft und Fabric API)
- **JDK 25 zum Ausführen von Gradle** (Stonecutter 0.10 setzt das voraus)
- JDK 21 für die 1.21.11-Kompilierung – fehlende Toolchains lädt der Foojay-Resolver automatisch nach
- Kein installiertes Gradle nötig, der Wrapper ist enthalten

```powershell
# PowerShell: Gradle mit JDK 25 starten (Pfad ggf. anpassen)
$env:JAVA_HOME = "C:\Program Files\Java\jdk-25.0.4.1"
.\gradlew build
```

In IntelliJ IDEA: *Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JVM*
auf das JDK 25 stellen. Die Java-Toolchain für die einzelnen Minecraft-Versionen wird vom Build
automatisch gewählt (21 bzw. 25).

## Erste Schritte

```powershell
# Aktive Version für IDE/normale Tasks wählen
.\gradlew "Set active project to 1.21.11"

# Alle Versionen bauen
.\gradlew build

# Einzelne Version bauen
.\gradlew :26.3:build

# Alle Versionen bauen und Jars sammeln (build/libs/<mod-version>/)
.\gradlew buildAndCollect

# Entwicklungsclient / -server starten (immer mit Versionspfad, sonst starten alle Versionen!)
.\gradlew :26.3:runClient
.\gradlew :26.3:runServer
.\gradlew :1.21.11:runClient
```

> Beim ersten Start dauert die Einrichtung einige Minuten, weil Gradle, Loom und alle
> Minecraft-/Fabric-Artefakte heruntergeladen werden.

## Projektstruktur

```
settings.gradle.kts            Stonecutter-Setup + Liste der Versionen
stonecutter.properties.toml    Mod-Metadaten, Loader-/API-Versionen pro Minecraft-Version
stonecutter.gradle.kts         Aktive Version + Code-Platzhalter (mod_version, minecraft)
build.gradle.kts               Gemeinsames Build-Script (wird auf jede Version angewendet)
src/main/java                  Gemeinsamer Quellcode aller Versionen
src/main/resources             Gemeinsame Ressourcen (fabric.mod.json, Assets, Mixins)
versions/                      Von Stonecutter generiert, nicht bearbeiten (siehe .gitignore)
run/                           Gemeinsames Spielverzeichnis für runClient/runServer
```

Wichtige Klassen:

- `de.xmoddevx.donuttweks.DonutTweks` – gemeinsamer Entrypoint, Mod-ID/Logger, `id(...)`-Helfer
- `de.xmoddevx.donuttweks.client.DonutTweksClient` – Client-Entrypoint (Keys, Rendering, Screens)
- `de.xmoddevx.donuttweks.registry.ModItems` – Item-Registrierung + Eintrag im Kreativ-Tab
- `assets/donuttweks/...` – Icon, Item-Modell, Textur, Übersetzungen

## Versionsspezifischer Code

Wird eine API zwischen den Versionen umbenannt oder verschoben, verwendet man Stonecutter-Kommentare.
Der Code dazwischen wird nur für die passenden Versionen kompiliert:

```java
//? if >=26.2 {
SomeNewApi.call();
//?} else {
/*SomeOldApi.call();
*///?}
```

- `>=`, `<=`, `<`, `>` und `=` werden gegen die Minecraft-Version ausgewertet.
- Beim Wechsel der aktiven Version schreibt Stonecutter die Dateien im `src/`-Ordner um.
- Für ganze Dateien, die nur für eine Version gelten, können versionierte Quellordner ergänzt werden
  (siehe Stonecutter-Dokumentation).

## Neues Item hinzufügen

1. Feld in `ModItems` anlegen, z. B. `public static final Item DONUT = register("donut", new Item.Properties());`
2. Modell unter `assets/donuttweks/items/<name>.json` und `assets/donuttweks/models/item/<name>.json` anlegen
3. Textur unter `assets/donuttweks/textures/item/<name>.png` ablegen
4. Übersetzung in `assets/donuttweks/lang/en_us.json` und `de_de.json` ergänzen
5. Optional: Eintrag im Kreativ-Tab über `ItemGroupEvents` (1.21.11) bzw. `CreativeModeTabEvents` (26.x)
   in `ModItems.initialize()`

## Mixins

- Allgemeine Mixins: `src/main/java/de/xmoddevx/donuttweks/mixin/` + `donuttweks.mixins.json`
- Client-Mixins: `src/main/java/de/xmoddevx/donuttweks/mixin/client/` + `donuttweks.client.mixins.json`

Die Konfigurationen sind aktuell leer und können direkt befüllt werden.

## Metadaten ändern

Mod-ID, Name, Gruppe und Version stehen in `stonecutter.properties.toml`:

```toml
mod.id = "donuttweks"
mod.name = "DonutTweks"
mod.group = "de.xmoddevx.donuttweks"
mod.version = "0.1.0"
```

Danach ggf. das Package in `src/main/java` umbenennen und die Pfade in `fabric.mod.json`,
den Mixin-Konfigurationen und den Assets anpassen.

## Lizenz

Alle Rechte vorbehalten – siehe `LICENSE`.
