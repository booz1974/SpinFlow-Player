# SpinFlow Player

Native Android-app (Kotlin, Jetpack Compose, Media3) voor een Music Assistant-server (MA):
wachtrij, spelers, groepsvolume, radio, zoeken, AI Radio DJ, telefoon-als-speler via
Sendspin en Android Auto. Package/namespace `nl.jeroen.massqueue`. De README beschrijft
per functie hoe die hoort te werken; lees het relevante deel voordat je iets verandert.

## Werkafspraken
- Schrijf in het Nederlands: code-commentaar, commits, README en antwoorden aan Jeroen.
- Jeroen is geen fulltime programmeur: leg kort uit wat je doet en waarom, zonder jargon.
- De repo is openbaar: nooit tokens, serveradressen, `local.properties` of sleutels committen.
  Het MA-token gaat alleen naar de eigen server en wordt versleuteld opgeslagen (`TokenCipher`).
- Teksten staan in `Localization.kt` en `res/values*/strings.xml` (Nederlands en Engels):
  werk beide talen bij.
- Werk de README bij als een functie verandert of erbij komt.
- MA-commando's: de server zelf toont wat er bestaat onder `/api-docs/commands`.

## Bouwen en testen
- `./gradlew assembleDebug` — debug-APK (JDK 17; `sendspin-jvm` komt via JitPack)
- `./gradlew testDebugUnitTest` — unittests (`app/src/test/java/nl/jeroen/massqueue/`)
- `./gradlew lint`
- `./gradlew assembleRelease` — release met R8; regels in `app/proguard-rules.pro`
  (Sendspin/Moshi heel laten). Controleer dit na wijzigingen aan Sendspin of modellen.
- Op Jeroens Windows-machine eerst `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"`.
- In een cloudsessie werkt een Gradle-build alleen als de omgeving de Android SDK heeft
  (`ANDROID_HOME`). Ontbreekt die, zeg dat dan in plaats van het te omzeilen.

## Opbouw
- `MassApiClient` (JSON-RPC `POST /api`), `MassEventSocket` (WebSocket `/ws`, live events),
  `MassViewModel` (toestand), `MassModels` (data), `SettingsStore` (instellingen)
- `ui/` — `PlayerScreen`, `SettingsScreen`, `DeviceNameScreen`, thema's
- `Sendspin*` + `SendspinPlaybackService` — telefoon als speler
- `PlaybackService`, `AutoBrowseTree`, `ArtworkProvider` — mediamelding en Android Auto
- `AdvertArt` — Commercial Break-hoes bij reclame

## Agents (`.claude/agents/`)
- `spinflow-tester` — na elke code-wijziging en voor een release: tests draaien en schrijven, fouten zoeken.
- `spinflow-ontwikkelaar` — na een nieuwe feature of voor een release: review van opbouw, veiligheid en batterij.
- `spinflow-gebruiker` — bij nieuwe schermen of teksten: feedback als huisgenoot, gast of in de auto.
Stel na een grotere wijziging voor om de tester en de ontwikkelaar te laten kijken.
