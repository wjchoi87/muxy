# Muxy Android

Native Android (Kotlin + Jetpack Compose) port of the iOS MuxyMobile client.

## Status

Phase 1 — connect & pair:

- [x] Project scaffold, Gradle wrapper, Compose
- [x] Encrypted device credential store (UUID + token)
- [x] Saved devices list with add/remove
- [x] WebSocket client (OkHttp) and `{type, payload}` envelope codec
- [x] `authenticateDevice` → on `401` fall back to `pairDevice`
- [ ] Manually verified end-to-end against a Mac running Muxy server

## Build

```sh
./gradlew assembleDebug
./gradlew test          # JSON envelope round-trip tests
./gradlew installDebug  # to a connected device or running emulator
```

## Test plan for Phase 1

1. Start the macOS Muxy server. Note the LAN IP and port (default `4865`).
2. Launch the Android app, tap **Add Device**, enter the host/port, tap **Add**.
3. Tap the device row → Android shows "Connecting…" then "Awaiting approval".
4. Approve the device on the Mac → Android shows "Connected" with a client ID.
5. Reopen the app and tap the same device row → it skips the approval step.
