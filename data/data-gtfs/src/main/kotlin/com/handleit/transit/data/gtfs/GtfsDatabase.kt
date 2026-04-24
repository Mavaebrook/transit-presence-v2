package com.handleit.transit.data.gtfs

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.handleit.transit.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.FileOutputStream
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransitDb @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val DB_NAME = "transit_prepopulated.db"
    }

    private var _db: SQLiteDatabase? = null

    private fun getDb(): SQLiteDatabase {
        val current = _db
        if ((current != null) && current.isOpen) return current

        return synchronized(this) {
            val secondCheck = _db
            if (secondCheck != null && secondCheck.isOpen) {
                secondCheck
            } else {
                val opened = openOrCopyDatabase()
                _db = opened
                
                // Run diagnostic on open
                try {
                    opened.rawQuery("SELECT count(*) FROM stops", null).use { if(it.moveToFirst()) Timber.d("DB Diagnostic: stops has ${it.getInt(0)} rows") }
                    opened.rawQuery("SELECT count(*) FROM stop_times", null).use { if(it.moveToFirst()) Timber.d("DB Diagnostic: stop_times has ${it.getInt(0)} rows") }
                    opened.rawQuery("SELECT count(*) FROM trips", null).use { if(it.moveToFirst()) Timber.d("DB Diagnostic: trips has ${it.getInt(0)} rows") }
                    opened.rawQuery("SELECT count(*) FROM calendar", null).use { if(it.moveToFirst()) Timber.d("DB Diagnostic: calendar has ${it.getInt(0)} rows") }
                } catch (e: Exception) {
                    Timber.e(e, "DB Diagnostic failed")
                }
                
                opened
            }
        }
    }

    private fun openOrCopyDatabase(): SQLiteDatabase {
        val dbFile = context.getDatabasePath(DB_NAME)
        if (!dbFile.exists()) {
            dbFile.parentFile?.mkdirs()
            try {
                context.assets.open(DB_NAME).use { input ->
                    FileOutputStream(dbFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Timber.i("TransitDb: Copied pre-built database from assets")
            } catch (e: Exception) {
                Timber.e(e, "TransitDb: Failed to copy asset database")
            }
        }
        return SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY)
    }

    // ── Emergency Diagnostic ──────────────────────────────────────────────────

    suspend fun runEmergencyDiagnostic() = withContext(Dispatchers.IO) {
        try {
            val db = getDb()
            Timber.d("===== EMERGENCY DB DUMP =====")

            // 1. Check Calendar (The most likely culprit)
            db.rawQuery("SELECT * FROM calendar LIMIT 5", null).use { cursor ->
                while (cursor.moveToNext()) {
                    val row = (0 until cursor.columnCount).joinToString { "${cursor.getColumnName(it)}=${cursor.getString(it)}" }
                    Timber.d("DUMP: calendar -> $row")
                }
            }

            // 2. Check Stop Times (Time format and whitespace)
            db.rawQuery("SELECT * FROM stop_times LIMIT 3", null).use { cursor ->
                while (cursor.moveToNext()) {
                    val row = (0 until cursor.columnCount).joinToString { "${cursor.getColumnName(it)}='${cursor.getString(it)}'" }
                    Timber.d("DUMP: stop_times -> $row")
                }
            }

            // 3. Check Trips (Joining logic)
            db.rawQuery("SELECT * FROM trips LIMIT 3", null).use { cursor ->
                while (cursor.moveToNext()) {
                    val row = (0 until cursor.columnCount).joinToString { "${cursor.getColumnName(it)}='${cursor.getString(it)}'" }
                    Timber.d("DUMP: trips -> $row")
                }
            }
            
            Timber.d("==============================")
        } catch (e: Exception) {
            Timber.e(e, "Emergency diagnostic failed")
        }
    }

    // ── Stops ─────────────────────────────────────────────────────────────────

    suspend fun getStopById(id: String): Stop? = withContext(Dispatchers.IO) {
        try {
            getDb().rawQuery(
                "SELECT stopId, stopName, lat, lng, wheelchairBoarding FROM stops WHERE stopId = ? LIMIT 1",
                arrayOf(id)
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.toStop() else null
            }
        } catch (e: Exception) {
            Timber.e(e, "TransitDb: getStopById failed")
            null
        }
    }

    suspend fun getNearbyStops(lat: Double, lng: Double, limit: Int = 20): List<Stop> =
        withContext(Dispatchers.IO) {
            try {
                getDb().rawQuery(
                    "SELECT stopId, stopName, lat, lng, wheelchairBoarding FROM stops ORDER BY ((lat - ?) * (lat - ?) + (lng - ?) * (lng - ?)) LIMIT ?",
                    arrayOf(lat.toString(), lat.toString(), lng.toString(), lng.toString(), limit.toString())
                ).use { cursor ->
                    val results = mutableListOf<Stop>()
                    while (cursor.moveToNext()) results.add(cursor.toStop())
                    results
                }
            } catch (e: Exception) {
                Timber.e(e, "TransitDb: getNearbyStops failed")
                emptyList()
            }
        }

    suspend fun getStopsInBounds(
        minLat: Double,
        maxLat: Double,
        minLng: Double,
        maxLng: Double
    ): List<Stop> = withContext(Dispatchers.IO) {
        try {
            getDb().rawQuery(
                """
                SELECT stopId, stopName, lat, lng, wheelchairBoarding
                FROM stops
                WHERE lat BETWEEN ? AND ?
                AND lng BETWEEN ? AND ?
                """.trimIndent(),
                arrayOf(
                    minLat.toString(),
                    maxLat.toString(),
                    minLng.toString(),
                    maxLng.toString()
                )
            ).use { cursor ->
                val results = mutableListOf<Stop>()
                while (cursor.moveToNext()) results.add(cursor.toStop())
                results
            }
        } catch (e: Exception) {
            Timber.e(e, "TransitDb: getStopsInBounds failed")
            emptyList()
        }
    }

    // ── Routes ────────────────────────────────────────────────────────────────

    suspend fun getAllRoutes(): List<Route> = withContext(Dispatchers.IO) {
        try {
            getDb().rawQuery(
                "SELECT routeId, routeShortName, routeLongName, routeType, routeColor, routeTextColor FROM routes ORDER BY routeShortName",
                null
            ).use { cursor ->
                val results = mutableListOf<Route>()
                while (cursor.moveToNext()) results.add(cursor.toRoute())
                results
            }
        } catch (e: Exception) {
            Timber.e(e, "TransitDb: getAllRoutes failed")
            emptyList()
        }
    }

    suspend fun getRoutesForStop(stopId: String): List<Route> = withContext(Dispatchers.IO) {
        try {
            getDb().rawQuery(
                """
                SELECT DISTINCT r.routeId, r.routeShortName, r.routeLongName,
                       r.routeType, r.routeColor, r.routeTextColor
                FROM routes r
                INNER JOIN trips t ON t.routeId = r.routeId
                INNER JOIN stop_times st ON st.tripId = t.tripId
                WHERE st.stopId = ?
                ORDER BY r.routeShortName
                """.trimIndent(),
                arrayOf(stopId)
            ).use { cursor ->
                val results = mutableListOf<Route>()
                while (cursor.moveToNext()) results.add(cursor.toRoute())
                results
            }
        } catch (e: Exception) {
            Timber.e(e, "TransitDb: getRoutesForStop failed")
            emptyList()
        }
    }

    fun observeAllRoutes(): Flow<List<Route>> = flow {
        emit(getAllRoutes())
    }

    // ── Trips ─────────────────────────────────────────────────────────────────

    suspend fun getStopsForTrip(
        routeId: String,
        directionId: Int,
        afterStopSequence: Int = 0,
    ): List<TripStop> = withContext(Dispatchers.IO) {
        try {
            getDb().rawQuery(
                """
                SELECT
                    s.stopId,
                    s.stopName,
                    s.lat,
                    s.lng,
                    st.arrivalTime,
                    st.departureTime,
                    st.stopSequence
                FROM stop_times st
                INNER JOIN trips t ON t.tripId = st.tripId
                INNER JOIN stops s ON s.stopId = st.stopId
                WHERE t.routeId = ?
                AND t.directionId = ?
                AND st.stopSequence > ?
                ORDER BY st.stopSequence ASC
                LIMIT 50
                """.trimIndent(),
                arrayOf(routeId, directionId.toString(), afterStopSequence.toString())
            ).use { cursor ->
                val results = mutableListOf<TripStop>()
                while (cursor.moveToNext()) {
                    results.add(
                        TripStop(
                            stopId = cursor.getString(0),
                            stopName = cursor.getString(1),
                            lat = cursor.getDouble(2),
                            lng = cursor.getDouble(3),
                            arrivalTime = cursor.getString(4),
                            departureTime = cursor.getString(5),
                            stopSequence = cursor.getInt(6),
                        )
                    )
                }
                results
            }
        } catch (e: Exception) {
            Timber.e(e, "TransitDb: getStopsForTrip failed")
            emptyList()
        }
    }

    // ── Nearby Departures ─────────────────────────────────────────────────────

    suspend fun getUpcomingDeparturesNearby(
        lat: Double,
        lng: Double,
        afterTime: String,
        radiusStops: Int = 10,
    ): List<UpcomingDeparture> = withContext(Dispatchers.IO) {
        try {
            val db = getDb()
            
            // EMERGENCY DIAGNOSTIC
            db.rawQuery("SELECT count(*) FROM stops", null).use { if(it.moveToFirst()) Timber.d("DIAGNOSTIC: stops=${it.getInt(0)}") }
            db.rawQuery("SELECT count(*) FROM stop_times", null).use { if(it.moveToFirst()) Timber.d("DIAGNOSTIC: stop_times=${it.getInt(0)}") }
            db.rawQuery("SELECT count(*) FROM calendar", null).use { if(it.moveToFirst()) Timber.d("DIAGNOSTIC: calendar=${it.getInt(0)}") }

            val dayOfWeek = LocalDate.now().dayOfWeek.name.lowercase()

            // Step 1 — get nearby stop IDs
            val nearbyStopIds = getDb().rawQuery(
                """
                SELECT stopId FROM stops
                ORDER BY ((lat - ?) * (lat - ?) + (lng - ?) * (lng - ?))
                LIMIT ?
                """.trimIndent(),
                arrayOf(lat.toString(), lat.toString(), lng.toString(), lng.toString(), radiusStops.toString())
            ).use { cursor ->
                val ids = mutableListOf<String>()
                while (cursor.moveToNext()) ids.add(cursor.getString(0))
                ids
            }

            Timber.d("getUpcomingDeparturesNearby: Found ${nearbyStopIds.size} nearby stops: ${nearbyStopIds.take(5)}")

            if (nearbyStopIds.isEmpty()) return@withContext emptyList()
            
            // Diagnostic for first stop
            nearbyStopIds.firstOrNull()?.let { firstId ->
                getDb().rawQuery("SELECT count(*) FROM stop_times WHERE stopId = ?", arrayOf(firstId)).use {
                    if (it.moveToFirst()) Timber.d("DB Diagnostic: Stop '$firstId' has ${it.getInt(0)} stop_times")
                }
                // Try trimmed check too
                getDb().rawQuery("SELECT count(*) FROM stop_times WHERE TRIM(stopId) = TRIM(?)", arrayOf(firstId)).use {
                    if (it.moveToFirst()) Timber.d("DB Diagnostic: Trimmed Stop '$firstId' has ${it.getInt(0)} stop_times")
                }
            }

            val normalizedTime = if (afterTime.length < 8) "0$afterTime" else afterTime
            val twoHoursLater = addHours(normalizedTime, 2)

            Timber.d("getUpcomingDeparturesNearby: Querying between $normalizedTime and $twoHoursLater on $dayOfWeek")

            // Step 2 — get all departures for those stops within next 2 hours
            // We use the substr('0' || ..., -8) trick to ensure HH:MM:SS comparison works
            // even if leading zeros are missing in the DB.
            val allDepartures = getDb().rawQuery(
                """
                SELECT
                    r.routeId, r.routeShortName, r.routeLongName, r.routeColor, r.routeTextColor,
                    t.tripHeadsign, t.directionId, st.departureTime, st.stopSequence, s.stopName, st.stopId
                FROM stop_times st
                INNER JOIN trips t ON t.tripId = st.tripId
                INNER JOIN routes r ON r.routeId = t.routeId
                INNER JOIN stops s ON s.stopId = st.stopId
                WHERE TRIM(st.stopId) IN (${nearbyStopIds.joinToString(",") { "'${it.trim()}'" }})
                AND substr('0' || TRIM(st.departureTime), -8) >= ?
                AND substr('0' || TRIM(st.departureTime), -8) <= ?
                ORDER BY substr('0' || TRIM(st.departureTime), -8) ASC
                """.trimIndent(),
                arrayOf(normalizedTime, twoHoursLater),
            ).use { cursor ->
                val results = mutableListOf<UpcomingDeparture>()
                while (cursor.moveToNext()) {
                    results.add(cursor.toUpcomingDeparture())
                }
                results
            }

            Timber.d("getUpcomingDeparturesNearby: Found ${allDepartures.size} departures in SQL window ($normalizedTime to $twoHoursLater)")

            // Step 3 — deduplicate by route+direction, keep soonest only
            val seen = mutableSetOf<String>()
            val deduplicated = allDepartures.filter { dep ->
                val key = "${dep.routeId}-${dep.directionId}"
                if (seen.contains(key)) false else {
                    seen.add(key)
                    true
                }
            }
            
            deduplicated

        } catch (e: Exception) {
            Timber.e(e, "TransitDb: getUpcomingDeparturesNearby failed")
            emptyList()
        }
    }

    // ── Single Stop Departures ────────────────────────────────────────────────

    suspend fun getUpcomingDepartures(
        stopId: String,
        afterTime: String,
        limit: Int = 30,
    ): List<UpcomingDeparture> = withContext(Dispatchers.IO) {
        try {
            val dayOfWeek = LocalDate.now().dayOfWeek.name.lowercase()
            val normalizedTime = if (afterTime.length < 8) "0$afterTime" else afterTime
            getDb().rawQuery(
                """
                SELECT
                    r.routeId,
                    r.routeShortName,
                    r.routeLongName,
                    r.routeColor,
                    r.routeTextColor,
                    t.tripHeadsign,
                    t.directionId,
                    st.departureTime,
                    st.stopSequence,
                    s.stopName,
                    st.stopId
                FROM stop_times st
                INNER JOIN trips t ON t.tripId = st.tripId
                INNER JOIN routes r ON r.routeId = t.routeId
                INNER JOIN stops s ON s.stopId = st.stopId
                LEFT JOIN calendar c ON t.serviceId = c.serviceId
                WHERE st.stopId = ?
                AND substr('0' || st.departureTime, -8) >= ?
                AND (c.$dayOfWeek = 1 OR c.serviceId IS NULL)
                ORDER BY substr('0' || st.departureTime, -8) ASC
                LIMIT ?
                """.trimIndent(),
                arrayOf(stopId, normalizedTime, limit.toString()),
            ).use { cursor ->
                val results = mutableListOf<UpcomingDeparture>()
                while (cursor.moveToNext()) results.add(cursor.toUpcomingDeparture())
                results
            }
        } catch (e: Exception) {
            Timber.e(e, "TransitDb: getUpcomingDepartures failed — falling back")
            fallbackSimpleQuery(stopId, afterTime, limit)
        }
    }

    private fun fallbackSimpleQuery(
        stopId: String,
        afterTime: String,
        limit: Int,
    ): List<UpcomingDeparture> {
        return try {
            val normalizedTime = if (afterTime.length < 8) "0$afterTime" else afterTime
            getDb().rawQuery(
                """
                SELECT
                    r.routeId,
                    r.routeShortName,
                    r.routeLongName,
                    r.routeColor,
                    r.routeTextColor,
                    t.tripHeadsign,
                    t.directionId,
                    st.departureTime,
                    st.stopSequence,
                    s.stopName,
                    st.stopId
                FROM stop_times st
                INNER JOIN trips t ON t.tripId = st.tripId
                INNER JOIN routes r ON r.routeId = t.routeId
                INNER JOIN stops s ON s.stopId = st.stopId
                WHERE st.stopId = ?
                AND substr('0' || st.departureTime, -8) >= ?
                ORDER BY substr('0' || st.departureTime, -8) ASC
                LIMIT ?
                """.trimIndent(),
                arrayOf(stopId, normalizedTime, limit.toString()),
            ).use { cursor ->
                val results = mutableListOf<UpcomingDeparture>()
                while (cursor.moveToNext()) results.add(cursor.toUpcomingDeparture())
                results
            }
        } catch (e: Exception) {
            Timber.e(e, "TransitDb: fallbackSimpleQuery failed")
            emptyList()
        }
    }

    // ── Debug Query ───────────────────────────────────────────────────────────

    suspend fun getDebugData(
        stopId: String?,
        routeShortName: String?,
        startTime: String?
    ): List<UpcomingDeparture> = withContext(Dispatchers.IO) {
        try {
            val sql = StringBuilder(
                """
                SELECT
                    r.routeId,
                    r.routeShortName,
                    r.routeLongName,
                    r.routeColor,
                    r.routeTextColor,
                    t.tripHeadsign,
                    t.directionId,
                    st.departureTime,
                    st.stopSequence,
                    s.stopName,
                    st.stopId
                FROM stop_times st
                INNER JOIN trips t ON t.tripId = st.tripId
                INNER JOIN routes r ON r.routeId = t.routeId
                INNER JOIN stops s ON s.stopId = st.stopId
                WHERE 1=1
                """.trimIndent()
            )

            val args = mutableListOf<String>()

            if (!stopId.isNullOrBlank()) {
                sql.append(" AND TRIM(st.stopId) = ?")
                args.add(stopId.trim())
            }
            if (!routeShortName.isNullOrBlank()) {
                sql.append(" AND TRIM(r.routeShortName) = ?")
                args.add(routeShortName.trim())
            }
            if (!startTime.isNullOrBlank()) {
                val normalizedStart = if (startTime.length < 8) "0$startTime" else startTime
                sql.append(" AND substr('0' || st.departureTime, -8) >= ?")
                args.add(normalizedStart.trim())
            }

            sql.append(" ORDER BY substr('0' || st.departureTime, -8) ASC LIMIT 100")

            getDb().rawQuery(sql.toString(), args.toTypedArray()).use { cursor ->
                val results = mutableListOf<UpcomingDeparture>()
                while (cursor.moveToNext()) results.add(cursor.toUpcomingDeparture())
                results
            }
        } catch (e: Exception) {
            Timber.e(e, "TransitDb: getDebugData failed")
            emptyList()
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun addHours(time: String, hours: Int): String {
        return try {
            val parts = time.split(":")
            val h = parts[0].toInt() + hours
            "%02d:%s:%s".format(h, parts[1], parts[2])
        } catch (_: Exception) {
            time
        }
    }

    private fun isTimeBetween(time: String, start: String, end: String): Boolean {
        fun normalize(t: String): String = if (t.length < 8) "0$t" else t
        val nt = normalize(time)
        val ns = normalize(start)
        val ne = normalize(end)
        return nt >= ns && nt <= ne
    }

    // ── Cursor Helpers ────────────────────────────────────────────────────────

    private fun android.database.Cursor.toStop() = Stop(
        stopId = getString(0),
        stopName = getString(1),
        lat = getDouble(2),
        lng = getDouble(3),
        wheelchairBoarding = getInt(4)
    )

    private fun android.database.Cursor.toRoute() = Route(
        routeId = getString(0),
        routeShortName = getString(1),
        routeLongName = getString(2),
        routeType = getInt(3),
        routeColor = getString(4) ?: "FFFFFF",
        routeTextColor = getString(5) ?: "000000"
    )

    private fun android.database.Cursor.toUpcomingDeparture() = UpcomingDeparture(
        routeId = getString(0),
        routeShortName = getString(1),
        routeLongName = getString(2),
        routeColor = getString(3) ?: "FFFFFF",
        routeTextColor = getString(4) ?: "000000",
        headsign = getString(5) ?: "",
        directionId = getInt(6),
        departureTime = getString(7),
        stopSequence = getInt(8),
        stopName = getString(9),
        stopId = getString(10),
    )
}
