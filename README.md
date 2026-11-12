# AutoScribe — Android (v1 • WireGuard only)

Native Android port of [AutoScribe](https://github.com/rasool-mrr/AutoScribe)
(desktop v1.2.0, commit `5e6bf53`), built with **Kotlin + Jetpack Compose**.
It finds the Windscribe WireGuard servers and ports that actually work on
your network — automatically cycling tunnels, measuring ping and packet
loss through each tunnel, and ranking the combinations that work best.

> **WireGuard only, by necessity.** The desktop app drives `windscribe-cli`,
> which does not exist on Android, and the Windscribe Android app exposes no
> public API to switch servers. This port instead drives the **official
> WireGuard app** through its documented remote-control broadcasts
> (`SET_TUNNEL_UP` / `SET_TUNNEL_DOWN`), using WireGuard configs generated
> from your Windscribe account. Stealth, WStunnel, IKEv2 and OpenVPN
> (UDP/TCP) cannot be tested this way — they need the desktop app.

## What you need

1. **A Windscribe Pro (or Build-A-Plan) account.** Generating WireGuard
   configs is a paid feature; free accounts cannot use the Config Generator.
2. **The WireGuard app** (Google Play or F-Droid).
3. **This app**, with the control permission granted (see setup).

## Setup (do once)

1. Install the WireGuard app. Open it, go to **Settings**, and turn on
   **“Allow remote control apps”**.
2. In your Windscribe account, open the **Config Generator → WireGuard**.
   For each location and port you want to test, generate a config and save
   it with the exact file name this app shows on the **Tunnels** tab, e.g.
   `WS-Amsterdam-Tulip-443.conf`. (The WireGuard app names an imported
   tunnel after its file name — the name must match exactly.)
3. Import the configs into the WireGuard app (it can import a whole folder
   or a zip of `.conf` files at once).
4. In the WireGuard app, toggle **any** tunnel on once by hand and tap **OK**
   on the system VPN permission prompt. This grants the VPN permission that
   remote control needs.
5. Open AutoScribe → **Tunnels** tab → **Grant control permission**. The
   WireGuard app prompts once; accept it.
6. On the **Tunnels** tab, tick the tunnels you imported. Only ticked
   tunnels are tested (unless you turn off “Only test imported tunnels”
   in the Test tab options).
7. Optional: use the **Probe** (▶) button on a tunnel to verify its name
   works before a long run. If a probe fails, the tunnel name in the
   WireGuard app does not match.
8. For long runs, turn off battery optimization for both AutoScribe and
   WireGuard so Android does not pause them mid-run.

## Features (ported from the desktop app)

- **Automatic testing** — server × WireGuard port, ports
  `443, 80, 53, 123, 1194, 65142` (toggleable, reorderable), running in a
  foreground service so it survives leaving the app.
- **Ping & packet loss** through the tunnel, measured with the system
  `ping` binary (same parsing rules as the desktop app); a labelled TCP
  fallback is used on devices where the ping binary is restricted.
- **Best routes** — leaderboard ordered by ping, then loss, then connect
  time; tap a route to connect to it.
- **Searchable location picker** — search country/city/server, filter by
  region, select whole countries or single servers; the test count is
  shown before you start. Nothing selected means every server (199 online
  servers in 74 countries in the built-in list).
- **Server list import** — import a newer `locations.json` (e.g. one
  refreshed by the desktop app). The desktop “Update list” download needs
  the windscribe.com auth token, which this app deliberately does not
  ship; the embedded list works fully offline.
- **Resume** (skip already-tested combinations), **Stop on first success**
  (stays connected), **Shuffle cities**, **Shuffle every test**.
- **Profiles** — save and load complete sets of settings.
- **Live dashboard** — current server, the four phases
  (disconnect → connect → settle → ping), progress, time left, success
  rate, best ping, and the technical log.
- **What works here** — success rate per port, with a tip when a port
  keeps failing (≥4 tests, <40% success).
- **Searchable, sortable results**, tap a row to copy or connect.
- **CSV export** with the same columns and file-name pattern as the
  desktop app (`{n}_{profile}_{timestamp}.csv`).
- **Three languages** — English, Farsi (right-to-left) and Russian,
  using the desktop app's own translations verbatim where they apply.
- **Same database schema** as the desktop app (`windscribe_results.db`,
  `tests` table), including the UNIQUE constraint and replace-on-retest.

## How it works

```
AutoScribe ── broadcast SET_TUNNEL_UP ──> WireGuard app ──> VPN tunnel
     │                                              │
     └─ ConnectivityManager (TRANSPORT_VPN?) ───────┘
     └─ ping 1.1.1.1 ──> SQLite ──> Best routes / CSV
```

“Connected” means Android reports an active VPN transport — the WireGuard
app publishes no external state broadcast, so this is the only signal a
third-party app can observe. A tunnel name that does not exist in the
WireGuard app never brings the VPN up, so that test times out as FAILURE.

## Project structure

```
app/src/main/
├── AndroidManifest.xml
├── java/com/autoscribe/android/
│   ├── MainActivity.kt           # tabs, language, permission launchers
│   ├── MainViewModel.kt          # settings, results, profiles, run prep
│   ├── AppDeps.kt                # shared DB / settings / WireGuard instances
│   ├── EngineState.kt            # StateFlows shared by service and UI
│   ├── TestService.kt            # foreground service + engine thread + wake lock
│   ├── data/
│   │   ├── Database.kt           # SQLite, desktop schema verbatim
│   │   ├── SettingsStore.kt      # SharedPreferences, desktop config.json shape
│   │   ├── ServerListImport.kt   # locations.json parsing (desktop rules)
│   │   ├── Strings.kt            # t() lookup, desktop i18n fallbacks
│   │   ├── LocationsData.kt      # GENERATED: 199 pops (see tools/)
│   │   └── StringsData.kt        # GENERATED: 244 keys x en/fa/ru (see tools/)
│   ├── engine/
│   │   ├── Models.kt             # Pop, TestResult, RunOptions, phases
│   │   ├── TunnelNaming.kt       # WS-<City>-<Node>-<port> contract
│   │   ├── PingParser.kt         # desktop ping-output parsing
│   │   ├── PingRunner.kt         # system ping + TCP fallback
│   │   ├── WireGuardController.kt# broadcasts + VPN-state checks
│   │   ├── TestEngine.kt         # the run loop, ported from _run()
│   │   ├── TestPlan.kt           # plan builder, best routes, port stats
│   │   └── CsvExport.kt          # CSV text + file names, desktop format
│   └── ui/
│       ├── theme/Theme.kt        # desktop palette, verbatim
│       ├── components/Common.kt  # cards, toggles, phase chips, dialogs
│       └── screens/              # Test, Results, BestRoutes, Tunnels, LocationPicker
├── res/                          # launcher icon, notification icon, theme
tools/extract_autoscribe_data.py  # regenerates LocationsData.kt + StringsData.kt
tests/PureLogicTest.kt           # standalone regression tests (see below)
```

## Build

Requirements: Android Studio (Hedgehog+) or command line with Android SDK
34 + JDK 17. No Gradle wrapper jar is shipped (same as the template this
project follows); in Android Studio the wrapper is generated for you, and
CI uses `gradle/actions/setup-gradle` with Gradle 8.10.2:

```bash
gradle assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Or push to GitHub: `.github/workflows/build-apk.yml` builds a debug APK on
every push to `main` and attaches it to a Release on `v*` tags.

Versions: AGP 8.5.2, Kotlin 2.0.20, Gradle 8.10.2, compileSdk/targetSdk 34,
minSdk 26, Compose BOM 2024.10.00.

## Tests

The pure engine files (no Android imports) are covered by standalone
Kotlin tests that run without an Android SDK:

```bash
bash tests/run_tests.sh   # needs a Kotlin compiler; see the script
```

The suite covers ping-output parsing (Android + Windows formats, summary
lines excluded, decimal commas), tunnel naming (uniqueness over all 199
pops × 6 ports, file-name safety), plan building (selection, shuffle,
chaos, imported filter, resume inputs), best-routes ordering, per-port
stats and the weak-port tip rule, and CSV quoting/file names.

## Honest limitations

- **WireGuard only.** No Stealth/WStunnel/IKEv2/OpenVPN testing.
- **Needs Pro configs, imported by hand.** There is no API to fetch
  configs; the Tunnels tab shows the exact file name for each one.
- **First VPN grant is manual.** Android requires the user to accept the
  VPN prompt once, inside the WireGuard app.
- **Ping numbers come from the device's ping binary** (or the labelled TCP
  fallback). They are comparable with each other, and with the desktop
  app's numbers on the same network, but they are not lab measurements.
- **Not compiled on the author's machine.** The UI layer is written
  against the same Compose/Material3 versions as a sibling project, and
  the pure engine is test-covered, but the first real compile happens in
  CI / Android Studio. If CI fails, the failure log names the file.

## License & attribution

This is a derivative of AutoScribe by Russell (rasool-mrr), released under
the **MIT License with a removal-on-request condition** — see `LICENSE`.
You may use, modify and publish it, but **if the author asks you to delete
or take down a copy or derivative, you must do so promptly (within 30
days)**. The copyright notice and this condition are included in every
copy, as the license requires.

AutoScribe is independent and unofficial: **not affiliated with, endorsed
by, or supported by Windscribe Limited**. “Windscribe” is a trademark of
its owner. You need your own Windscribe account, and you are responsible
for following Windscribe's terms of service and the laws where you live.
“WireGuard” is a registered trademark of Jason A. Donenfeld.
