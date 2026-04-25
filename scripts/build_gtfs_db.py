#!/usr/bin/env python3
"""
build_gtfs_db.py - Final LYNX Edition
Optimized for Central Florida LYNX GTFS data.
Features:
- Whitespace stripping and ID normalization.
- Time normalization (HH:MM:SS).
- Distance-based linear interpolation for intermediate stops.
- Advanced SQL indexing.
"""

import zipfile
import urllib.request
import csv
import io
import os
import sys
import sqlite3
import math

# ---------------- CONFIG ----------------
GTFS_URL = "http://gtfsrt.golynx.com/gtfsrt/google_transit.zip"
OUTPUT_DB = "app/src/main/assets/transit_prepopulated.db"

# ---------------- HELPERS ----------------

def clean(val):
    """Trims whitespace and removes leading zeros from numeric IDs."""
    if val is None: return ""
    s = str(val).strip()
    if s.isdigit():
        return str(int(s))
    return s

def normalize_time(t):
    """Ensures time is HH:MM:SS with leading zero."""
    if not t: return None
    parts = t.strip().split(':')
    if len(parts) != 3: return t
    return f"{int(parts[0]):02d}:{parts[1]}:{parts[2]}"

def time_to_seconds(t):
    """Converts HH:MM:SS to seconds since midnight."""
    if not t: return None
    p = t.split(':')
    return int(p[0]) * 3600 + int(p[1]) * 60 + int(p[2])

def seconds_to_time(s):
    """Converts seconds since midnight back to HH:MM:SS."""
    h = int(s // 3600)
    m = int((s % 3600) // 60)
    sec = int(s % 60)
    return f"{h:02d}:{m:02d}:{sec:02d}"

def get_dist(p1, p2):
    """Haversine distance in meters between (lat, lon)."""
    if not p1 or not p2: return 0.0
    R = 6371000
    phi1, phi2 = math.radians(p1[0]), math.radians(p2[0])
    dphi = math.radians(p2[0] - p1[0])
    dlambda = math.radians(p2[1] - p1[1])
    a = math.sin(dphi/2)**2 + math.cos(phi1)*math.cos(phi2)*math.sin(dlambda/2)**2
    return 2 * R * math.atan2(math.sqrt(a), math.sqrt(1-a))

def iter_csv(zf, filename):
    if filename not in zf.namelist(): return
    with zf.open(filename) as f:
        content = io.TextIOWrapper(f, encoding="utf-8-sig")
        reader = csv.DictReader(content, skipinitialspace=True)
        for row in reader:
            yield row

# ---------------- CORE LOGIC ----------------

def build_db(gtfs_bytes):
    print("Starting Clean Build...")
    os.makedirs("app/src/main/assets", exist_ok=True)
    if os.path.exists(OUTPUT_DB): os.remove(OUTPUT_DB)

    conn = sqlite3.connect(OUTPUT_DB)
    cursor = conn.cursor()

    cursor.executescript("""
        PRAGMA synchronous = OFF;
        PRAGMA journal_mode = MEMORY;

        CREATE TABLE stops (stopId TEXT PRIMARY KEY, stopName TEXT, lat REAL, lng REAL);
        CREATE TABLE routes (routeId TEXT PRIMARY KEY, routeShortName TEXT, routeLongName TEXT, routeType INT, routeColor TEXT, routeTextColor TEXT);
        CREATE TABLE trips (tripId TEXT PRIMARY KEY, routeId TEXT, serviceId TEXT, tripHeadsign TEXT, directionId INT);
        CREATE TABLE stop_times (tripId TEXT, stopId TEXT, stopSequence INT, arrivalTime TEXT, departureTime TEXT, PRIMARY KEY(tripId, stopSequence));
        CREATE TABLE calendar (serviceId TEXT PRIMARY KEY, monday INT, tuesday INT, wednesday INT, thursday INT, friday INT, saturday INT, sunday INT);
    """)

    with zipfile.ZipFile(io.BytesIO(gtfs_bytes)) as zf:
        # 1. Map Coordinates
        print("Mapping stop coordinates...")
        stop_map = {}
        stop_data = []
        for r in iter_csv(zf, "stops.txt"):
            sid = clean(r["stop_id"])
            lat, lng = float(r["stop_lat"]), float(r["stop_lon"])
            stop_map[sid] = (lat, lng)
            stop_data.append((sid, clean(r["stop_name"]), lat, lng))
        cursor.executemany("INSERT INTO stops VALUES (?,?,?,?)", stop_data)

        # 2. Routes & Calendar
        print("Parsing routes and calendar...")
        cursor.executemany("INSERT INTO routes VALUES (?,?,?,?,?,?)",
            ((clean(r["route_id"]), clean(r["route_short_name"]), clean(r["route_long_name"]), int(r.get("route_type",3)), clean(r.get("route_color","FFFFFF")), clean(r.get("route_text_color","000000"))) for r in iter_csv(zf, "routes.txt")))

        cursor.executemany("INSERT INTO calendar VALUES (?,?,?,?,?,?,?,?)",
            ((clean(r["service_id"]), int(r["monday"]), int(r["tuesday"]), int(r["wednesday"]), int(r["thursday"]), int(r["friday"]), int(r["saturday"]), int(r["sunday"])) for r in iter_csv(zf, "calendar.txt")))

        # 3. Trips
        print("Parsing trips...")
        cursor.executemany("INSERT INTO trips VALUES (?,?,?,?,?)",
            ((clean(r["trip_id"]), clean(r["route_id"]), clean(r["service_id"]), clean(r.get("trip_headsign","")), int(r.get("direction_id",0))) for r in iter_csv(zf, "trips.txt")))

        # 4. Stop Times with Interpolation
        print("Processing Stop Times (Interpolating gaps)...")
        current_trip = None
        trip_buffer = []

        def flush_trip(buffer):
            if not buffer: return
            # Step A: Identify segments between timing points
            # segments = [(index_of_start, index_of_end), ...]
            times = [normalize_time(r['arrival_time']) for r in buffer]
            known_indices = [i for i, t in enumerate(times) if t is not None]

            for k in range(len(known_indices) - 1):
                start_idx = known_indices[k]
                end_idx = known_indices[k+1]

                if end_idx - start_idx <= 1: continue # No gaps to fill

                # Calculate distances
                segment_dists = [0.0] # distance from start to each stop in segment
                total_dist = 0.0
                for i in range(start_idx, end_idx):
                    d = get_dist(stop_map.get(buffer[i]['stop_id']), stop_map.get(buffer[i+1]['stop_id']))
                    total_dist += d
                    segment_dists.append(total_dist)

                # Calculate times
                t1 = time_to_seconds(times[start_idx])
                t2 = time_to_seconds(times[end_idx])
                duration = t2 - t1

                # Fill the gaps
                if total_dist > 0:
                    for i in range(1, end_idx - start_idx):
                        ratio = segment_dists[i] / total_dist
                        interpolated_sec = t1 + (duration * ratio)
                        times[start_idx + i] = seconds_to_time(interpolated_sec)
                else:
                    # Fallback to even distribution if distances are unknown
                    for i in range(1, end_idx - start_idx):
                        ratio = i / (end_idx - start_idx)
                        times[start_idx + i] = seconds_to_time(t1 + (duration * ratio))

            # Final Step: Insert cleaned and filled rows
            rows = []
            for i, r in enumerate(buffer):
                final_t = times[i] or "00:00:00"
                rows.append((clean(r['trip_id']), clean(r['stop_id']), int(r['stop_sequence']), final_t, final_t))
            cursor.executemany("INSERT INTO stop_times VALUES (?,?,?,?,?)", rows)

        # Main loop for stop_times.txt
        for r in iter_csv(zf, "stop_times.txt"):
            tid = clean(r["trip_id"])
            if tid != current_trip:
                flush_trip(trip_buffer)
                trip_buffer = []
                current_trip = tid
            trip_buffer.append(r)
        flush_trip(trip_buffer)

    print("Generating optimized indexes...")
    cursor.executescript("""
        CREATE INDEX idx_stoptimes_stop ON stop_times(stopId);
        CREATE INDEX idx_stoptimes_time ON stop_times(departureTime);
        CREATE INDEX idx_stoptimes_lookup ON stop_times(stopId, departureTime);
        CREATE INDEX idx_trips_service ON trips(serviceId);
        CREATE INDEX idx_trips_route ON trips(routeId);
    """)

    conn.commit()
    cursor.execute("VACUUM")
    conn.close()
    print(f"SUCCESS: {OUTPUT_DB} is ready.")

def main():
    print("Downloading GTFS static feed...")
    try:
        req = urllib.request.Request(GTFS_URL, headers={"User-Agent": "TransitPresence/1.0"})
        with urllib.request.urlopen(req, timeout=120) as response:
            build_db(response.read())
    except Exception as e:
        print(f"FAILED: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()
