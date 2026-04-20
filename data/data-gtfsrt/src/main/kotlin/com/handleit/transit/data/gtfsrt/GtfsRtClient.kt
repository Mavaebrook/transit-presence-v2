package com.handleit.transit.data.gtfsrt

import com.handleit.transit.model.TripUpdate
import com.handleit.transit.model.VehiclePosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GtfsRtClient @Inject constructor(
    private val httpClient: OkHttpClient
) {

    suspend fun fetchVehiclePositions(feedUrl: String): List<VehiclePosition> =
        withContext(Dispatchers.IO) {
            fetchBytes(feedUrl)?.let { GtfsRtParser.parseVehiclePositions(it) } ?: emptyList()
        }

    suspend fun fetchTripUpdates(feedUrl: String): List<TripUpdate> =
        withContext(Dispatchers.IO) {
            fetchBytes(feedUrl)?.let { GtfsRtParser.parseTripUpdates(it) } ?: emptyList()
        }

    private fun fetchBytes(url: String): ByteArray? {
        val request = Request.Builder().url(url).build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.w("GtfsRtClient: HTTP ${response.code} for $url")
                    return null
                }
                response.body?.bytes()
            }
        } catch (e: Exception) {
            Timber.e(e, "GtfsRtClient: fetchBytes failed for $url")
            null
        }
    }
}
