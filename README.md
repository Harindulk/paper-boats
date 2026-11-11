# Paper Boats (working title)

A couch party game where every phone is a controller. The desktop app is the big screen: a river
race for up to eight paper boats. Friends scan a QR code, type a name, and steer with one thumb and
paddle with the other. Nobody installs anything: the controller is a web page the desktop serves
itself, built from the same Kotlin as the game.

Made with Kotlin Multiplatform and Compose Multiplatform for the Kotlin Multiplatform Contest 2026.

## How it works

```
 phone browser (Kotlin/Wasm)  ──WebSocket──►  desktop host (Kotlin/JVM, Ktor server)
   ControllerApp, ControllerClient              PartySession, Race, RiverView
              └──────── shared commonMain: rules, protocol, UI ────────┘
```

Everything that matters lives in `composeApp/src/commonMain`:

| Shared code | What it does |
|---|---|
| `game/Race.kt` | The race rules: steering, paddling, lanterns, rocks, ramming, a catch-up current. No UI, no clock, deterministic for a seed. It reports what happened as `RaceEvent`s. |
| `party/PartySession.kt` | Lobby, countdown, race, results and rematch; seats and colours for up to eight players; a phone that drops out gets its own boat back. |
| `protocol/Messages.kt` | The messages between phone and host, as `kotlinx.serialization` sealed interfaces. |
| `party/ControllerClient.kt` | The phone's WebSocket link (Ktor client). Runs unchanged in the browser and on the JVM. |
| `ui/` | Every screen in Compose: the lobby, countdown, river, scoreboard and results on the big screen, and the whole phone app. |
| `ui/fx/`, `ui/RiverFx.kt` | Easing, particles and a camera with screen shake, driven by the race events. Pure Kotlin with tests. |

Platform code is small: the desktop adds the Ktor server, the LAN address and a QR code; the web
target adds its entry point, vibration and saving the player's seat in the browser. Counting
non-blank lines of Kotlin, 1,150 of 1,358 (85%) are in `commonMain`.

## Run it

Requirements: JDK 17 or newer. The first build downloads Gradle, Kotlin and Node for the web build.

```bash
./gradlew :composeApp:run
```

The desktop window shows a QR code and an address such as `http://192.168.1.24:8642/`. Phones on the
same Wi-Fi open it, enter a name and grab a boat. Press **Space** on the computer to start a race and
again for a rematch. No phones around? Press **K** to add a keyboard player (A / D / W, then the arrow keys).

On Windows use `gradlew.bat` instead of `./gradlew`. If phones cannot connect, allow Java through the
firewall for private networks.

## Play

- Left half of the phone: slide your thumb left or right to steer.
- Right half: press to paddle for a burst of speed.
- Collect the floating lanterns and keep off the rocks.
- Paddle into another boat to spin it out and take one of its lanterns.
- Most lanterns after 60 seconds wins. The phone buzzes when you pick one up, hit a rock or win.

## Tests

```bash
./gradlew :composeApp:desktopTest
```

- Race rules: determinism, staying on the river, steering, paddling, ramming, the catch-up current,
  timing, and one event per pickup or crash.
- Party session: seats, colours, the countdown, the lobby to results cycle, rejoining mid-race,
  protocol JSON.
- Effects: easing curves, particle limits, a camera that settles and a shake that dies away.
- Network: phones (the same `ControllerClient` the browser uses) join a real host over WebSockets,
  race, get buzzed for their own crashes only, reconnect to the same boat, and download the
  controller gzipped.
- Screens: the big screen and the phone are rendered off-screen to `composeApp/build/renders`.

## Status

- [x] Shared rules, session, protocol and screens
- [x] Desktop host with the controller server and QR lobby
- [x] Web controller (Kotlin/Wasm) over WebSockets, with a loading screen and a message for browsers too old to run it
- [x] Countdown, ramming, haptics, reconnecting phones, particles and a lantern finish
- [ ] Android controller app
- [ ] Sound

## Licence

MIT, see [LICENSE](LICENSE).
