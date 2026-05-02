# Migration cleanup for `~/Projects/muxy` (mac app repo)

The iOS app and the `MuxyShared` module have been extracted to
`~/Projects/muxy-mobile`. The mac app repo can drop everything mobile.

## What moved to `muxy-mobile`

- `MuxyMobile/` (entire directory)
- `MuxyMobile.xcodeproj/` (entire directory)
- `MuxyShared/` (entire directory — vendored copy)
- `.swiftformat`, `.swiftlint.yml`, `.tool-versions`, `.gitattributes`
- `scripts/run-mobile.sh`
- `.github/workflows/release-ios.yml` (now `ios-release.yml`)
- iOS portion of `.github/workflows/checks.yml` (now `ios-checks.yml`)

## Cleanup checklist (run inside `~/Projects/muxy`)

### 1. Remove mobile sources

```sh
git rm -r MuxyMobile MuxyMobile.xcodeproj
git rm scripts/run-mobile.sh
```

### 2. Decide what to do with `MuxyShared`

`MuxyShared` is consumed by both the mac app (`Muxy` target in `Package.swift`)
and the iOS app. It was vendored into `muxy-mobile/ios/MuxyShared/` so the iOS
side is self-contained.

**Option A — keep two copies (recommended short term).** Leave `MuxyShared/`
in `~/Projects/muxy` as the source of truth for the mac app. When the protocol
changes, copy updated files into `muxy-mobile/ios/MuxyShared/` and update the
Android side to match. Document the protocol version somewhere visible.

**Option B — promote `MuxyShared` to its own Swift package repo** and have
both `~/Projects/muxy` and `muxy-mobile/ios/Package.swift` depend on it via
`XCRemoteSwiftPackageReference`. Cleaner, but adds release ceremony.

**Do not delete `MuxyShared/` from `~/Projects/muxy` until a strategy is
chosen** — the mac app's `Muxy` and `MuxyServer` targets depend on it.

### 3. Update `Package.swift`

Nothing to change — `Package.swift` only references the mac targets and
`MuxyShared`. The iOS app was never a Package.swift target.

### 4. Update workflows

- `release-ios.yml` — delete; lives in `muxy-mobile` now.
- `checks.yml` — remove the iOS-specific block:
  - the `mobile` paths-filter
  - the `Build (iOS)` step gated on `steps.changes.outputs.mobile`
  - drop SwiftLint/SwiftFormat install steps **only if** the mac side doesn't
    use them (it does — keep them).

### 5. Update tooling configs

- `.swiftformat`, `.swiftlint.yml`, `.tool-versions` — keep, the mac app uses
  them too.

### 6. Update docs / scripts

- `README.md` — drop iOS sections, link to `muxy-mobile`.
- `CLAUDE.md` / `AGENTS.md` — drop any iOS-specific instructions.
- `scripts/setup.sh`, `scripts/checks.sh`, `scripts/build-release.sh` — audit
  for iOS-specific commands; should already be mac-only.

### 7. Issue / PR templates

- `.github/ISSUE_TEMPLATE/` and `pull_request_template.md` — update if they
  reference the iOS app.

## Sanity checks after cleanup

```sh
swift build           # mac targets still build
swift test            # tests still pass
scripts/checks.sh     # lint/format clean
```

## Protocol drift watch

The iOS and Android apps each carry their own copy of the wire protocol
(`MuxyMessage`, `MuxyProtocol`, `ProtocolParams`, the `*DTO` types).
Whenever the mac app changes the protocol:

1. Update `~/Projects/muxy/MuxyShared/` (mac source of truth).
2. Copy the changed Swift files into `muxy-mobile/ios/MuxyShared/`.
3. Update the Kotlin equivalents in `muxy-mobile/android/app/src/main/.../protocol/`.

Bump a `PROTOCOL_VERSION` constant in all three places to fail fast on
mismatched clients.
