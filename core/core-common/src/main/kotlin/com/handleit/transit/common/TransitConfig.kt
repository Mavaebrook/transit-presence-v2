package com.handleit.transit.common

/**
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║              TRANSIT PRESENCE — APP CONFIGURATION                       ║
 * ║                                                                          ║
 * ║  This is the ONLY file you need to edit to configure the app.           ║
 * ║  Replace every "YOUR_..." placeholder with your actual values.           ║
 * ║                                                                          ║
 * ║  Sections:                                                               ║
 * ║    1. API Keys        — Maps key                                         ║
 * ║    2. Map Provider    — Google or OpenStreetMap toggle                   ║
 * ║    3. GTFS Feed URLs  — Live bus data (LYNX pre-filled)                  ║
 * ║    4. Tuning Values   — Thresholds, intervals, radii                     ║
 * ║    5. Debug / Dev     — Mock mode defaults                               ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 */
object TransitConfig {

    // ─────────────────────────────────────────────────────────────────────────
    // SECTION 1: API KEYS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Google Maps API Key.
     * Only required when MAP_PROVIDER = MapProvider.GOOGLE (see Section 2).
     *
     * How to get one:
     *   1. Go to https://console.cloud.google.com
     *   2. Enable "Maps SDK for Android"
     *   3. Create a credential → API Key
     *   4. Restrict it to package name: com.handleit.transit
     *
     * If using OpenStreetMap, leave this as the placeholder — it is ignored.
     */
    const val MAPS_API_KEY = "YOUR_MAPS_API_KEY_HERE"

    // ─────────────────────────────────────────────────────────────────────────
    // SECTION 2: MAP PROVIDER
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Choose your default map provider.
     *
     * MapProvider.GOOGLE
     *   - Requires MAPS_API_KEY above
     *   - Smooth vector tiles, familiar UI
     *   - Requires Google Play Services on device
     *
     * MapProvider.OSM  (OpenStreetMap via OSMDroid)
     *   - No API key, fully open source, zero cost
     *   - Works on devices without Google Play Services
     *   - Tiles cached locally for offline use
     *   - Transit-focused tile layer available
     *
     * Users can toggle between providers at runtime in Settings.
     * This value sets the default when the app is first installed.
     */
    val MAP_PROVIDER_DEFAULT: MapProvider = MapProvider.OSM

    // ─────────────────────────────────────────────────────────────────────────
    // SECTION 3: GTFS FEED URLs  (Central Florida / LYNX — pre-filled)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * GTFS-Realtime: Live vehicle positions.
     * Returns protobuf binary — tells us where every bus is right now.
     * Pre-filled with the official LYNX GTFS-RT endpoint.
     */
    const val GTFS_RT_VEHICLE_POSITIONS_URL =
        "http://gtfsrt.golynx.com/gtfsrt/GTFS_VehiclePositions.pb"

    /**
     * GTFS-Realtime: Trip updates / arrival predictions.
     * Returns protobuf binary — predicted arrival time per stop.
     * Pre-filled with the official LYNX GTFS-RT endpoint.
     */
    const val GTFS_RT_TRIP_UPDATES_URL =
        "http://gtfsrt.golynx.com/gtfsrt/GTFS_TripUpdates.pb"

    /**
     * GTFS Static base URL.
     * Base path for downloading the static GTFS schedule ZIP.
     */
    const val GTFS_STATIC_BASE_URL =
        "http://gtfsrt.golynx.com/gtfsrt/"

    /**
     * GTFS Static ZIP download URL.
     * The full schedule/stops/routes/shapes file.
     * Downloaded once on first launch and cached in Room DB.
     */
    const val GTFS_STATIC_ZIP_URL =
        "https://www.golynx.com/plan-your-trip/google-transit.stml"

    // ─────────────────────────────────────────────────────────────────────────
    // SECTION 4: TUNING VALUES
    // These defaults work well for Central Florida. Adjust only if needed.
    // ─────────────────────────────────────────────────────────────────────────

    /** Stop geofence radius in meters. Increase to 75 for busy intersections. */
    const val STOP_GEOFENCE_RADIUS_METERS = 50f

    /** ON_BUS confidence threshold (0.0–1.0). 0.85 = 85% confident. */
    const val ON_BUS_CONFIDENCE_THRESHOLD = 0.85f

    /** How often to poll GTFS-RT feeds (milliseconds). */
    const val GTFS_RT_POLL_INTERVAL_MS = 10_000L

    /** Radius (meters) around user to track nearby bus routes on map. */
    const val NEARBY_ROUTES_RADIUS_METERS = 500f

    /** Max number of nearby routes to show moving on the map. */
    const val MAX_NEARBY_ROUTES_ON_MAP = 5

    /** Stops before destination to trigger "prepare to exit" alert. */
    const val EXIT_PREP_STOPS_BEFORE = 3

    /** Stops before destination to trigger "pull cord now" alert. */
    const val EXIT_ALERT_STOPS_BEFORE = 1

    /** ETA thresholds in seconds that trigger boarding escalation. */
    const val ETA_T_PASSIVE_SECS = 300L   // 5 min — gentle notification
    const val ETA_T_ACTIVE_SECS  = 120L   // 2 min — heads-up alert
    const val ETA_T_STRONG_SECS  = 90L    // 90 sec — vibration + UI
    const val ETA_T_BOARD_SECS   = 30L    // 30 sec — board now

    // ─────────────────────────────────────────────────────────────────────────
    // SECTION 5: DEVELOPMENT / DEBUG
    // ─────────────────────────────────────────────────────────────────────────

    /** Mock mode default location — Downtown Orlando LYNX Transfer Hub. */
    const val MOCK_STOP_LAT = 28.5421
    const val MOCK_STOP_LNG = -81.3790
}

/** Map provider options. Add new providers here as needed. */
enum class MapProvider {
    GOOGLE,  // Google Maps SDK — requires API key
    OSM,     // OpenStreetMap via OSMDroid — no key required
}
