# Paper Boats (working title)

A couch party game where every phone is a controller. The desktop app is the big screen: a river
race for up to 8 paper boats. Players steer with one thumb and paddle with the other.

Built with Kotlin Multiplatform and Compose Multiplatform for the Kotlin Multiplatform Contest 2026.
The game rules, network protocol and every screen are shared Kotlin code.

## Status

Work in progress.

- [x] Shared race rules (`composeApp/src/commonMain/.../game`) with tests
- [x] Shared big-screen view, scoreboard and phone controller UI
- [x] Desktop app: two players on one keyboard
- [ ] Desktop host serves the controller page and accepts phones over the local network
- [ ] Web controller (Kotlin/Wasm) connected over WebSockets
- [ ] Android controller app

## Run

Requirements: JDK 17 or newer.

```bash
./gradlew :composeApp:run            # desktop host: player 1 uses A / D / W, player 2 uses the arrow keys
./gradlew :composeApp:desktopTest    # race rule tests, plus renders of the screens to composeApp/build/renders
```

## Licence

MIT, see [LICENSE](LICENSE).
