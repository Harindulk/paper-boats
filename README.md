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
| `game/Race.kt` | The race rules: steering, paddling, lights, rocks, a catch-up current. No UI, no clock, deterministic for a seed. |
| `party/PartySession.kt` | Lobby, race, results and rematch; seats and colours for up to eight players. |
| `protocol/Messages.kt` | The messages between phone and host, as `kotlinx.serialization` sealed interfaces. |
| `party/ControllerClient.kt` | The phone's WebSocket link (Ktor client). Runs unchanged in the browser and on the JVM. |
| `ui/` | The river, scoreboard, phone pad and the whole phone app, in Compose. |

Platform code is small: the desktop adds the Ktor server, the LAN address and a QR code; the web
target adds a five-line entry point.

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
- Collect the floating lights, avoid the rocks. Most lights after 60 seconds wins.

## Tests

```bash
./gradlew :composeApp:desktopTest
```

- Race rules: determinism, staying on the river, steering, paddling, the catch-up current, timing.
- Party session: seats, colours, the full lobby to results cycle, ignored strangers, protocol JSON.
- Network: two controllers (the same `ControllerClient` the phones use) join a real host over
  WebSockets, race, and hear their final places; the host serves the controller page.
- Screens: the lobby, race, results and phone pad are rendered off-screen to `composeApp/build/renders`.

## Status

- [x] Shared rules, session, protocol and screens
- [x] Desktop host with the controller server and QR lobby
- [x] Web controller (Kotlin/Wasm) over WebSockets
- [ ] Android controller app
- [ ] Sound, art polish, reconnecting phones

## Licence

MIT, see [LICENSE](LICENSE).
