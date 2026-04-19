# Transit Presence v2
### Smart Ride Assistant · Central Florida · HandleIT.Online

---

## Quick Start — 3 steps to a working APK

### Step 1: Configure the app
Open **one file** and fill in your values:
```
core/core-common/src/main/kotlin/com/handleit/transit/common/TransitConfig.kt
```

The LYNX GTFS-RT feed URLs are **already filled in**. You only need to add:
- Your Google Maps API key (or switch `MAP_PROVIDER_DEFAULT` to `MapProvider.OSM` for no-key required)

### Step 2: Add Maps key to local.properties (for local builds)
Create `local.properties` in the project root (this file is gitignored):
```
sdk.dir=/path/to/your/android/sdk
MAPS_API_KEY=your_actual_key_here
```

### Step 3: Build
```bash
./gradlew assembleDebug
```
APK output: `app/build/outputs/apk/debug/app-debug.apk`

---

## Module Structure

```
app/                    Thin shell — DI wiring + navigation
core/
  core-model/           Pure Kotlin domain models (no Android deps)
  core-fsm/             State machine — 8 states, fully unit tested
  core-common/          TransitConfig + ConfigValidator
data/
  data-gtfs/            Room DB + GTFS static CSV parser
  data-gtfsrt/          Live bus feed client (manual proto parsing)
  data-location/        GPS, geofencing, sensor fusion engine
feature/
  feature-map/          Full-screen map (Google or OpenStreetMap)
  feature-riding/       Boarding, on-bus, and exit alert screens
  feature-settings/     Settings + debug overlay
service/
  service-tracking/     Foreground service (correct startForeground timing)
```

---

## Map Provider Toggle

Two map providers built in — switch anytime in Settings:

| | Google Maps | OpenStreetMap |
|---|---|---|
| API Key Required | Yes | **No** |
| Works without Play Services | No | **Yes** |
| Offline tiles | No | **Yes** |
| Visual quality | High | Good |

Default set in `TransitConfig.kt` → `MAP_PROVIDER_DEFAULT`

---

## GTFS-RT Data Sources (Central Florida / LYNX)

| Feed | URL |
|---|---|
| Vehicle Positions | `http://gtfsrt.golynx.com/gtfsrt/GTFS_VehiclePositions.pb` |
| Trip Updates | `http://gtfsrt.golynx.com/gtfsrt/GTFS_TripUpdates.pb` |

Pre-filled. Change only if adding a second agency.

---

## GitHub Actions CI

The workflow at `.github/workflows/android.yml`:
- Triggers on push to `main` or `master`
- Builds debug APK
- Runs FSM unit tests
- Uploads APK as a downloadable artifact (30-day retention)

To add your Maps key to CI without committing it:
1. Go to your GitHub repo → Settings → Secrets → Actions
2. Add secret: `MAPS_API_KEY` = your key
3. The workflow reads it automatically

---

## Adding a Second Agency (e.g. SunRail)

1. Add new URL constants to `TransitConfig.kt`
2. Add a second `GtfsRtClient` binding in `AppModule.kt`
3. Tag it with a `@Named("sunrail")` qualifier

---

*Built by HandleIT.Online*
