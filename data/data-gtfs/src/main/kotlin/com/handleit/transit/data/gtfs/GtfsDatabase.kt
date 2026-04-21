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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransitDb @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val DB_NAME = "transit_prepopulated.db"
    }

    private var _db: SQLiteDatabase? = null

    private fun getDb(): SQLiteDatabase {
        val current = _db
        if (current != null && current.isOpen) return current

        return synchronized(this) {
            val secondCheck = _db
            if (secondCheck != null && secondCheck.isOpen) {
                secondCheck
            } else {
                val opened = openOrCopyDatabase()
                _db = opened
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

    // ── Departures ────────────────────────────────────────────────────────────

    /**
     * Returns upcoming departures from a stop after [afterTime].
     * [afterTime] format: "HH:MM:SS" — pass current time of day.
     * GTFS times can exceed 24:00:00 for overnight trips so string
     * comparison works correctly as-is.
     * [limit] controls max results per direction.
     */
    suspend fun getUpcomingDepartures(
        stopId: String,
        afterTime: String,
        limit: Int = 5,
    ): List<UpcomingDeparture> = withContext(Dispatchers.IO) {
        try {
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
                    s.stopName
                FROM stop_times st
                INNER JOIN trips t ON t.tripId = st.tripId
                INNER JOIN routes r ON r.routeId = t.routeId
                INNER JOIN stops s ON s.stopId = st.stopId
                WHERE st.stopId = ?
                AND st.departureTime >= ?
                ORDER BY st.departureTime ASC
                LIMIT ?
                """.trimIndent(),
                arrayOf(stopId, afterTime, limit.toString())
            ).use { cursor ->
                val results = mutableListOf<UpcomingDeparture>()
                while (cursor.moveToNext()) {
                    results.add(
                        UpcomingDeparture(
                            routeId = cursor.getString(0),
                            routeShortName = cursor.getString(1),
                            routeLongName = cursor.getString(2),
                            routeColor = cursor.getString(3) ?: "FFFFFF",
                            routeTextColor = cursor.getString(4) ?: "000000",
                            headsign = cursor.getString(5) ?: "",
                            directionId = cursor.getInt(6),
                            departureTime = cursor.getString(7),
                            stopSequence = cursor.getInt(8),
                            stopName = cursor.getString(9),
                        )
                    )
                }
                results
            }
        } catch (e: Exception) {
            Timber.e(e, "TransitDb: getUpcomingDepartures failed")
            emptyList()
        }
    }

    /**
     * Returns all stops for a given trip in sequence order.
     * Used to build the timeline screen.
     */
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

    fun observeAllRoutes(): Flow<List<Route>> = flow {
        emit(getAllRoutes())
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
}
