package com.handleit.transit.data.gtfs

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.handleit.transit.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TransitDb
 *
 * Plain SQLite implementation — no Room, no annotation processing,
 * no identity hash conflicts.
 *
 * On first launch, copies the pre-built database from assets to the
 * app's database directory and opens it directly.
 */
@Singleton
class TransitDb @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val DB_NAME = "transit_prepopulated.db"
    }

    private val db: SQLiteDatabase by lazy {
        openOrCopyDatabase()
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
        return SQLiteDatabase.openDatabase(
            dbFile.path,
            null,
            SQLiteDatabase.OPEN_READONLY,
        )
    }

    // ── Stops ─────────────────────────────────────────────────────────────────

    suspend fun getStopById(id: String): Stop? = withContext(Dispatchers.IO) {
        try {
            db.rawQuery(
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
                db.rawQuery(
                    """
                    SELECT stopId, stopName, lat, lng, wheelchairBoarding
                    FROM stops
                    ORDER BY ((lat - ?) * (lat - ?) + (lng - ?) * (lng - ?))
                    LIMIT ?
                    """.trimIndent(),
                    arrayOf(
                        lat.toString(), lat.toString(),
                        lng.toString(), lng.toString(),
                        limit.toString()
                    )
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

    suspend fun getStopCount(): Int = withContext(Dispatchers.IO) {
        try {
            db.rawQuery("SELECT COUNT(*) FROM stops", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }
        } catch (e: Exception) {
            0
        }
    }

    // ── Routes ────────────────────────────────────────────────────────────────

    suspend fun getRouteById(id: String): Route? = withContext(Dispatchers.IO) {
        try {
            db.rawQuery(
                "SELECT routeId, routeShortName, routeLongName, routeType, routeColor, routeTextColor FROM routes WHERE routeId = ? LIMIT 1",
                arrayOf(id)
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.toRoute() else null
            }
        } catch (e: Exception) {
            Timber.e(e, "TransitDb: getRouteById failed")
            null
        }
    }

    suspend fun getAllRoutes(): List<Route> = withContext(Dispatchers.IO) {
        try {
            db.rawQuery(
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

    fun observeAllRoutes(): Flow<List<Route>> = flow {
        emit(getAllRoutes())
    }

    // ── Trips ─────────────────────────────────────────────────────────────────

    suspend fun getTripById(id: String): Trip? = withContext(Dispatchers.IO) {
        try {
            db.rawQuery(
                "SELECT tripId, routeId, serviceId, tripHeadsign, directionId, shapeId FROM trips WHERE tripId = ? LIMIT 1",
                arrayOf(id)
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.toTrip() else null
            }
        } catch (e: Exception) {
            Timber.e(e, "TransitDb: getTripById failed")
            null
        }
    }

    suspend fun getTripsByRoute(routeId: String): List<Trip> = withContext(Dispatchers.IO) {
        try {
            db.rawQuery(
                "SELECT tripId, routeId, serviceId, tripHeadsign, directionId, shapeId FROM trips WHERE routeId = ?",
                arrayOf(routeId)
            ).use { cursor ->
                val results = mutableListOf<Trip>()
                while (cursor.moveToNext()) results.add(cursor.toTrip())
                results
            }
        } catch (e: Exception) {
            Timber.e(e, "TransitDb: getTripsByRoute failed")
            emptyList()
        }
    }

    // ── Stop Times ────────────────────────────────────────────────────────────

    suspend fun getStopTimesForTrip(tripId: String): List<StopTime> =
        withContext(Dispatchers.IO) {
            try {
                db.rawQuery(
                    "SELECT tripId, stopId, stopSequence, arrivalTime, departureTime FROM stop_times WHERE tripId = ? ORDER BY stopSequence",
                    arrayOf(tripId)
                ).use { cursor ->
                    val results = mutableListOf<StopTime>()
                    while (cursor.moveToNext()) results.add(cursor.toStopTime())
                    results
                }
            } catch (e: Exception) {
                Timber.e(e, "TransitDb: getStopTimesForTrip failed")
                emptyList()
            }
        }

    // ── Shapes ────────────────────────────────────────────────────────────────

    suspend fun getShape(shapeId: String): List<ShapePoint> = withContext(Dispatchers.IO) {
        try {
            db.rawQuery(
                "SELECT shapeId, lat, lng, sequence FROM shapes WHERE shapeId = ? ORDER BY sequence",
                arrayOf(shapeId)
            ).use { cursor ->
                val results = mutableListOf<ShapePoint>()
                while (cursor.moveToNext()) results.add(cursor.toShapePoint())
                results
            }
        } catch (e: Exception) {
            Timber.e(e, "TransitDb: getShape failed")
            emptyList()
        }
    }

    // ── Cursor extension helpers ───────────────────────────────────────────────

    private fun android.database.Cursor.toStop() = Stop(
        stopId = getString(0),
        stopName = getString(1),
        lat = getDouble(2),
        lng = getDouble(3),
        wheelchairBoarding = getInt(4),
    )

    private fun android.database.Cursor.toRoute() = Route(
        routeId = getString(0),
        routeShortName = getString(1),
        routeLongName = getString(2),
        routeType = getInt(3),
        routeColor = getString(4) ?: "FFFFFF",
        routeTextColor = getString(5) ?: "000000",
    )

    private fun android.database.Cursor.toTrip() = Trip(
        tripId = getString(0),
        routeId = getString(1),
        serviceId = getString(2),
        tripHeadsign = getString(3) ?: "",
        directionId = getInt(4),
        shapeId = getString(5) ?: "",
    )

    private fun android.database.Cursor.toStopTime() = StopTime(
        tripId = getString(0),
        stopId = getString(1),
        stopSequence = getInt(2),
        arrivalTime = getString(3) ?: "",
        departureTime = getString(4) ?: "",
    )

    private fun android.database.Cursor.toShapePoint() = ShapePoint(
        shapeId = getString(0),
        lat = getDouble(1),
        lng = getDouble(2),
        sequence = getInt(3),
    )
}
