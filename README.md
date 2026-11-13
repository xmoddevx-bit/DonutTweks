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
- `de.xmoddevx.donuttweks.client.DonutTweksClient` – Client-Entrypoint (Keybind + Tick-Event)
- `de.xmoddevx.donuttweks.client.AutoSellController` – Auto-Verkauf-Logik (siehe unten)
- `de.xmoddevx.donuttweks.client.gui.AutoSellScreen` – Auswahl- und Einstellungsfenster
- `de.xmoddevx.donuttweks.registry.ModItems` – Item-Registrierung + Eintrag im Kreativ-Tab
- `assets/donuttweks/...` – Icon, Item-Modell, Textur, Übersetzungen

## Auto-Verkauf (Auktionshaus)

Mit **R** (änderbar unter *Optionen → Steuerung → DonutTweks*) öffnest du den Auto-Verkauf:

1. Item aus dem Inventar anklicken (das Item muss im Inventar liegen)
2. **Preis pro Auktion** eintragen – ganze Zahlen oder Kurzschreibweise (`35000`, `35k`, `1.2m`, `3b`).
   Ohne `k`/`m`/`b` sind nur reine Ziffern erlaubt (`35.000` wird abgelehnt, weil es sonst als `35`
   gelesen würde – nimm `35k`). Die Schreibweise wird **genau so** gesendet, wie du sie eintippst
   (`20k` bleibt `20k`), weil der Server diese Form direkt akzeptiert.
3. **Items pro Auktion** (1–64), **Aufteilen** und **Verkaufen** einstellen.
   Bei aktivem Aufteilen werden volle Inventarstapel in entsprechend große Verkaufsstapel zerlegt.
4. **Starten** – das Fenster schließt sich und die Mod sendet wiederholt:

```
/ah sell <preis>
```

Vor jedem Befehl wird der nächste passende Stack per Inventar-Klick in den aktuell ausgewählten
Hotbar-Slot gelegt, damit das Auktionshaus-Plugin den richtigen Gegenstand in der Hand hat. Bei
aktivem Aufteilen wird der übrige Stapel zurück ins Inventar gelegt. Das läuft weiter, bis kein passendes Item mehr im
Inventar liegt. Bei **Items pro Auktion = 1** werden zum Beispiel 64 Items nacheinander einzeln gelistet.

- **Es gibt keine Wartezeit zwischen den Verkäufen:** Sobald der Server den Stapel aus der Hand
  entfernt hat, geht im nächsten Tick der nächste Befehl raus. Es gibt kein Intervall mehr.
- Antwortet der Server gar nicht (Lag, Plugin hängt), wird der Befehl einfach wiederholt statt
  abzubrechen. Der Lauf stoppt nur noch, wenn keine Items mehr da sind, die Welt verlassen wird,
  ein Container-GUI aufgeht oder sich das Item nicht in die Hand legen lässt.
- **R** öffnet während des Laufs das Einstellungsfenster; der *Stoppen*-Button beendet den Lauf.
- Nur ein echtes **Container-GUI** (Kiste, Auktionshaus, Plugin-Menü) stoppt den Lauf, weil es das
  Inventar-Menü ersetzt. Inventar (`E`), Chat und Pause sind harmlos – du kannst beim Verkaufen
  zusehen. So wird nie blind durch Container geklickt.
- Antwortet der Server 10 Sekunden lang auf keinen Befehl, schickt die Mod einmalig `/ah`, um zu
  prüfen, ob Befehle überhaupt ankommen. Die Antwort steht im Log (`[AutoSell-Diag]`).
- Standard: 1 Item pro Auktion (Preis `20k`), Aufteilen und Verkaufen aktiviert.
- Lehnt das Fenster einen Start ab (kein Item gewählt, Item nicht mehr im Inventar, ungültiger
  Preis), erscheint der Grund rot im Fenster **und** im Chat.

Voraussetzungen und Grenzen:

- Das Auktionshaus-Plugin muss `/ah sell <preis>` für den Gegenstand in der Hand unterstützen.
- Öffnet das Plugin ein Bestätigungsfenster, bricht die Mod ab (siehe oben) – der Klick auf
  "Bestätigen" müsste dann manuell erfolgen.
- Items werden über den Item-Typ erkannt. Verschiedene Varianten desselben Items (z. B. mit
  eigenem Namen) werden also gemeinsam verkauft.
- **Wichtig:** Automatisierte Befehle/Makros sind auf vielen Servern verboten. Prüfe die Regeln
  deines Servers, bevor du das benutzt.

Technisch nutzt der Auto-Verkauf nur Client-APIs: den Keybind über die Fabric-Key-API, das
Inventar-Swap über einen normalen Inventar-Klick (`handleContainerInput`/`handleInventoryMouseClick`
mit `SWAP`) und den Befehl über `ClientPacketListener#sendCommand`. Es sind keine Mixins nötig.

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
mod.version = "0.1.2"
```

Danach ggf. das Package in `src/main/java` umbenennen und die Pfade in `fabric.mod.json`,
den Mixin-Konfigurationen und den Assets anpassen.

## Lizenz

Alle Rechte vorbehalten – siehe `LICENSE`.
