package com.handleit.transit.data.location

import com.handleit.transit.common.TransitConfig
import com.handleit.transit.model.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BayesianFusionEngine
 *
 * Combines multiple signals into a single ON_BUS confidence score [0.0–1.0].
 *
 * Weight table (sums to 1.0 when all signals available):
 *   GTFS trip match      0.40  — direct vehicle ID match, highest weight
 *   Speed                0.25  — must exceed walking threshold
 *   Route alignment      0.20  — position matches route polyline
 *   Wi-Fi SSID           0.10  — optional, degrades gracefully
 *   Vehicle motion       0.05  — accelerometer pattern
 *
 * If a signal is unavailable (e.g. Wi-Fi = -1f), its weight is
 * redistributed proportionally to the remaining signals.
 */
@Singleton
class SensorFusionEngine @Inject constructor() {

    companion object {
        private const val WALKING_SPEED_MPS    = 1.39f   // 5 km/h
        private const val MIN_VEHICLE_SPEED    = 2.78f   // 10 km/h
        private const val MAX_VEHICLE_SPEED    = 16.67f  // 60 km/h
    }

    fun compute(
        bundle: SignalBundle,
        threshold: Float = TransitConfig.ON_BUS_CONFIDENCE_THRESHOLD,
    ): FusionResult {
        val speedScore   = speedScore(bundle.location.speedMps)
        val routeScore   = bundle.routeAlignmentScore.coerceIn(0f, 1f)
        val gtfsScore    = bundle.gtfsTripConfidence.coerceIn(0f, 1f)
        val wifiScore    = bundle.wifiConfidence.coerceIn(0f, 1f)
        val motionScore  = bundle.motionScore.coerceIn(0f, 1f)

        val wifiAvailable = bundle.wifiConfidence >= 0f

        // Build weight map — zero out unavailable signals then renormalise
        val raw = mutableMapOf(
            "gtfs_trip"    to 0.40f,
            "speed"        to 0.25f,
            "route_align"  to 0.20f,
            "wifi_ssid"    to if (wifiAvailable) 0.10f else 0f,
            "motion"       to 0.05f,
        )
        val total = raw.values.sum()
        val weights = raw.mapValues { (_, w) -> if (total > 0) w / total else 0f }

        val scores = mapOf(
            "gtfs_trip"    to gtfsScore,
            "speed"        to speedScore,
            "route_align"  to routeScore,
            "wifi_ssid"    to wifiScore,
            "motion"       to motionScore,
        )

        val breakdown = weights.mapValues { (k, w) -> w * (scores[k] ?: 0f) }
        val confidence = breakdown.values.sum().coerceIn(0f, 1f)
        val dominant   = breakdown.maxByOrNull { it.value }?.key ?: "unknown"

        return FusionResult(
            onBusConfidence = confidence,
            dominantSignal  = dominant,
            breakdown       = breakdown,
            meetsThreshold  = confidence >= threshold,
        )
    }

    private fun speedScore(mps: Float): Float = when {
        mps < WALKING_SPEED_MPS   -> 0f
        mps < MIN_VEHICLE_SPEED   -> ((mps - WALKING_SPEED_MPS) /
                                       (MIN_VEHICLE_SPEED - WALKING_SPEED_MPS)) * 0.5f
        else -> 0.5f + (((mps - MIN_VEHICLE_SPEED) /
                          (MAX_VEHICLE_SPEED - MIN_VEHICLE_SPEED)).coerceIn(0f, 1f)) * 0.5f
    }
}
