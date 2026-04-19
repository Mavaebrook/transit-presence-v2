package com.handleit.transit.data.gtfs

import androidx.room.*
import com.handleit.transit.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

// ─── Room Entities ────────────────────────────────────────────────────────────

@Entity(tableName = "stops")
data class StopEntity(
    @PrimaryKey val stopId: String,
    val stopName: String,
    val lat: Double,
    val lng: Double,
    val wheelchairBoarding: Int = 0,
) {
    fun toModel() = Stop(stopId, stopName, lat, lng, wheelchairBoarding)
}

@Entity(tableName = "routes")
data class RouteEntity(
    @PrimaryKey val routeId: String,
    val routeShortName: String,
    val routeLongName: String,
    val routeType: Int,
    val routeColor: String = "FFFFFF",
    val routeTextColor: String = "000000",
) {
    fun toModel() = Route(routeId, routeShortName, routeLongName, routeType, routeColor, routeTextColor)
}

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey val tripId: String,
    val routeId: String,
    val serviceId: String,
    val tripHeadsign: String = "",
    val directionId: Int = 0,
    val shapeId: String = "",
) {
    fun toModel() = Trip(tripId, routeId, serviceId, tripHeadsign, directionId, shapeId)
}

@Entity(tableName = "stop_times", primaryKeys = ["tripId", "stopSequence"])
data class StopTimeEntity(
    val tripId: String,
    val stopId: String,
    val stopSequence: Int,
    val arrivalTime: String,
    val departureTime: String,
) {
    fun toModel() = StopTime(tripId, stopId, stopSequence, arrivalTime, departureTime)
}

@Entity(tableName = "shapes", primaryKeys = ["shapeId", "sequence"])
data class ShapeEntity(
    val shapeId: String,
    val lat: Double,
    val lng: Double,
    val sequence: Int,
) {
    fun toModel() = ShapePoint(shapeId, lat, lng, sequence)
}

// ─── DAOs ─────────────────────────────────────────────────────────────────────

@Dao
interface StopDao {
    @Query("SELECT * FROM stops WHERE stopId = :id LIMIT 1")
    suspend fun getById(id: String): StopEntity?

    @Query("""
        SELECT * FROM stops
        ORDER BY ((lat - :lat) * (lat - :lat) + (lng - :lng) * (lng - :lng))
        LIMIT :limit
    """)
    suspend fun getNearby(lat: Double, lng: Double, limit: Int = 20): List<StopEntity>

    @Query("SELECT COUNT(*) FROM stops")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(stops: List<StopEntity>)
}

@Dao
interface RouteDao {
    @Query("SELECT * FROM routes WHERE routeId = :id LIMIT 1")
    suspend fun getById(id: String): RouteEntity?

    @Query("SELECT * FROM routes ORDER BY routeShortName")
    fun observeAll(): Flow<List<RouteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(routes: List<RouteEntity>)
}

@Dao
interface TripDao {
    @Query("SELECT * FROM trips WHERE tripId = :id LIMIT 1")
    suspend fun getById(id: String): TripEntity?

    @Query("SELECT * FROM trips WHERE routeId = :routeId")
    suspend fun getByRoute(routeId: String): List<TripEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(trips: List<TripEntity>)
}

@Dao
interface StopTimeDao {
    @Query("SELECT * FROM stop_times WHERE tripId = :tripId ORDER BY stopSequence")
    suspend fun getForTrip(tripId: String): List<StopTimeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(stopTimes: List<StopTimeEntity>)
}

@Dao
interface ShapeDao {
    @Query("SELECT * FROM shapes WHERE shapeId = :shapeId ORDER BY sequence")
    suspend fun getShape(shapeId: String): List<ShapeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(points: List<ShapeEntity>)
}

// ─── Database ─────────────────────────────────────────────────────────────────

@Database(
    entities = [StopEntity::class, RouteEntity::class, TripEntity::class,
                StopTimeEntity::class, ShapeEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class TransitDatabase : RoomDatabase() {
    abstract fun stopDao(): StopDao
    abstract fun routeDao(): RouteDao
    abstract fun tripDao(): TripDao
    abstract fun stopTimeDao(): StopTimeDao
    abstract fun shapeDao(): ShapeDao
}

// ─── GTFS Static Parser ───────────────────────────────────────────────────────

@Singleton
class GtfsStaticParser @Inject constructor(
    private val db: TransitDatabase,
) {
    suspend fun parseZip(stream: InputStream) = withContext(Dispatchers.IO) {
        Timber.i("GTFS: Starting static feed parse")
        ZipInputStream(stream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val bytes = zip.readBytes()
                when (entry.name) {
                    "stops.txt"      -> parseStops(bytes.inputStream())
                    "routes.txt"     -> parseRoutes(bytes.inputStream())
                    "trips.txt"      -> parseTrips(bytes.inputStream())
                    "stop_times.txt" -> parseStopTimes(bytes.inputStream())
                    "shapes.txt"     -> parseShapes(bytes.inputStream())
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        Timber.i("GTFS: Parse complete — ${db.stopDao().count()} stops loaded")
    }

    private suspend fun parseStops(stream: InputStream) {
        val entities = parseCsv(stream) { row ->
            StopEntity(
                stopId = row["stop_id"] ?: return@parseCsv null,
                stopName = row["stop_name"] ?: "",
                lat = row["stop_lat"]?.toDoubleOrNull() ?: return@parseCsv null,
                lng = row["stop_lon"]?.toDoubleOrNull() ?: return@parseCsv null,
                wheelchairBoarding = row["wheelchair_boarding"]?.toIntOrNull() ?: 0,
            )
        }
        db.stopDao().insertAll(entities)
    }

    private suspend fun parseRoutes(stream: InputStream) {
        val entities = parseCsv(stream) { row ->
            RouteEntity(
                routeId = row["route_id"] ?: return@parseCsv null,
                routeShortName = row["route_short_name"] ?: "",
                routeLongName = row["route_long_name"] ?: "",
                routeType = row["route_type"]?.toIntOrNull() ?: 3,
                routeColor = row["route_color"] ?: "FFFFFF",
                routeTextColor = row["route_text_color"] ?: "000000",
            )
        }
        db.routeDao().insertAll(entities)
    }

    private suspend fun parseTrips(stream: InputStream) {
        val entities = parseCsv(stream) { row ->
            TripEntity(
                tripId = row["trip_id"] ?: return@parseCsv null,
                routeId = row["route_id"] ?: return@parseCsv null,
                serviceId = row["service_id"] ?: "",
                tripHeadsign = row["trip_headsign"] ?: "",
                directionId = row["direction_id"]?.toIntOrNull() ?: 0,
                shapeId = row["shape_id"] ?: "",
            )
        }
        db.tripDao().insertAll(entities)
    }

    private suspend fun parseStopTimes(stream: InputStream) {
        val batch = mutableListOf<StopTimeEntity>()
        parseCsvStreaming(stream) { row ->
            val entity = StopTimeEntity(
                tripId = row["trip_id"] ?: return@parseCsvStreaming,
                stopId = row["stop_id"] ?: return@parseCsvStreaming,
                stopSequence = row["stop_sequence"]?.toIntOrNull() ?: return@parseCsvStreaming,
                arrivalTime = row["arrival_time"] ?: "",
                departureTime = row["departure_time"] ?: "",
            )
            batch.add(entity)
            if (batch.size >= 5000) {
                db.stopTimeDao().insertAll(batch.toList())
                batch.clear()
            }
        }
        if (batch.isNotEmpty()) db.stopTimeDao().insertAll(batch)
    }

    private suspend fun parseShapes(stream: InputStream) {
        val batch = mutableListOf<ShapeEntity>()
        parseCsvStreaming(stream) { row ->
            val entity = ShapeEntity(
                shapeId = row["shape_id"] ?: return@parseCsvStreaming,
                lat = row["shape_pt_lat"]?.toDoubleOrNull() ?: return@parseCsvStreaming,
                lng = row["shape_pt_lon"]?.toDoubleOrNull() ?: return@parseCsvStreaming,
                sequence = row["shape_pt_sequence"]?.toIntOrNull() ?: return@parseCsvStreaming,
            )
            batch.add(entity)
            if (batch.size >= 5000) {
                db.shapeDao().insertAll(batch.toList())
                batch.clear()
            }
        }
        if (batch.isNotEmpty()) db.shapeDao().insertAll(batch)
    }

    // ── CSV helpers ───────────────────────────────────────────────────────────

    private fun <T> parseCsv(stream: InputStream, mapper: (Map<String, String>) -> T?): List<T> {
        val results = mutableListOf<T>()
        parseCsvStreaming(stream) { row -> mapper(row)?.let { results.add(it) } }
        return results
    }

    private fun parseCsvStreaming(stream: InputStream, onRow: (Map<String, String>) -> Unit) {
        stream.bufferedReader().useLines { lines ->
            var headers: List<String>? = null
            for (line in lines) {
                if (line.isBlank()) continue
                val values = splitCsv(line)
                if (headers == null) { headers = values; continue }
                onRow(headers.zip(values).toMap())
            }
        }
    }

    private fun splitCsv(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        for (c in line) {
            when {
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> { result.add(current.toString().trim()); current.clear() }
                else -> current.append(c)
            }
        }
        result.add(current.toString().trim())
        return result
    }
}
