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
