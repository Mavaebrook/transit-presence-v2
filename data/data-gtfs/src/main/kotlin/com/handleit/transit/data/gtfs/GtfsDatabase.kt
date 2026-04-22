package com.handleit.transit.data.gtfs

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.handleit.transit.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.FileOutputStream
import java.time.LocalDate
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

    // ── Debug Query ───────────────────────────────────────────────────────────

    suspend fun getDebugData(
        stopId: String?,
        routeShortName: String?,
        startTime: String?
    ): List<UpcomingDeparture> = withContext(Dispatchers.IO) {
        try {
            val sql = StringBuilder("""
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
            """.trimIndent())

            val args = mutableListOf<String>()

            if (!stopId.isNullOrBlank()) {
                sql.append(" AND st.stopId = ?")
                args.add(stopId)
            }
            if (!routeShortName.isNullOrBlank()) {
                sql.append(" AND r.routeShortName = ?")
                args.add(routeShortName)
            }
            if (!startTime.isNullOrBlank()) {
                sql.append(" AND st.departureTime >= ?")
                args.add(startTime)
            }

            sql.append(" ORDER BY st.departureTime ASC LIMIT 100")

            getDb().rawQuery(sql.toString(), args.toTypedArray()).use { cursor ->
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
                            stopId = cursor.getString(10) // Fixed missing mapping
                        )
                    )
                }
                results
            }
        } catch (e: Exception) {
            Timber.e(e, "TransitDb: getDebugData failed")
            emptyList()
        }
    }

    // ── Upcoming Departures ───────────────────────────────────────────────────

    suspend fun getUpcomingDepartures(
        stopId: String,
        afterTime: String,
        limit: Int = 30,
    ): List<UpcomingDeparture> = withContext(Dispatchers.IO) {
        try {
            val dayOfWeek = LocalDate.now().dayOfWeek.name.lowercase()

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
                AND st.departureTime >= ?
                AND (c.$dayOfWeek = 1 OR c.serviceId IS NULL)
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
                            stopId = cursor.getString(10)
                        )
                    )
                }
                results
            }
        } catch (e: Exception) {
            Timber.e(e, "TransitDb: getUpcomingDepartures failed. Falling back.")
            fallbackSimpleQuery(stopId, afterTime, limit)
        }
    }

    private fun fallbackSimpleQuery(
        stopId: String,
        afterTime: String,
        limit: Int
    ): List<UpcomingDeparture> {
        return try {
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
                            stopId = cursor.getString(10) // Fixed missing mapping
                        )
                    )
                }
                results
            }
        } catch (e: Exception) {
            Timber.e(e, "TransitDb: fallbackSimpleQuery failed")
            emptyList()
        }
    }
}
