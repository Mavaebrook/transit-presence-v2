package com.handleit.transit.data.location

import com.google.android.gms.maps.model.LatLng

data class LocationSnapshot(
    val latLng: LatLng,
    val accuracyMeters: Float,
    val speedMps: Float,
    val bearingDeg: Float,
    val timestampMs: Long
)