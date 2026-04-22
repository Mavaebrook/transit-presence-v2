#!/usr/bin/env python3
"""
build_gtfs_db.py
Downloads LYNX GTFS feed and generates a compiled SQLite DB
Updated to include calendar.txt for day-of-week filtering.
"""

import zipfile
import urllib.request
import csv
import io
import os
import sys
import sqlite3

# ---------------- GTFS SOURCES ----------------
GTFS_URL = "http://gtfsrt.golynx.com/gtfsrt/google_transit.zip"
FALLBACK_URL = "http://www.golynx.com/lynxmap/GoLYNX_data/google_transit.zip"

OUTPUT_DB = "app/src/main/assets/transit_prepopulated.db"

# ---------------- DOWNLOAD ----------------

def download_gtfs():
    print("Downloading GTFS static feed...")
    for url in [GTFS_URL, FALLBACK_URL]:
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "TransitPresence/1.0"})
            with urllib.request.urlopen(req, timeout=60) as response:
                data = response.read()
                if len(data) > 10000 and data[:2] == b"PK":
                    print(f"Downloaded {len(data):,} bytes from {url}")
                    return data
        except Exception as e:
            print(f"Failed {url}: {e}")
    raise Exception("All GTFS download URLs failed")

def iter_csv_from_zip(zf, filename):
    if filename not in zf.namelist():
        print(f"Skipping {filename}")
        return
    with zf.open(filename) as f:
        content = io.TextIOWrapper(f, encoding="utf-8-sig")
        reader = csv.DictReader(content)
        for row in reader:
            yield row

# ---------------- BUILD SQLITE DB ----------------

def build_db(gtfs_bytes):
    os.makedirs("app/src/main/assets", exist_ok=True)
    if os.path.exists(OUTPUT_DB):
        os.remove(OUTPUT_DB)

    conn = sqlite3.connect(OUTPUT_DB)
    cursor = conn.cursor()

    cursor.executescript("""
        PRAGMA synchronous = OFF;
        PRAGMA journal_mode = MEMORY;

        CREATE TABLE stops (
            stopId TEXT PRIMARY KEY,
            stopName TEXT NOT NULL,
            lat REAL NOT NULL,
            lng REAL NOT NULL,
            wheelchairBoarding INTEGER DEFAULT 0
        );

        CREATE TABLE routes (
            routeId TEXT PRIMARY KEY,
            routeShortName TEXT NOT NULL,
            routeLongName TEXT NOT NULL,
            routeType INTEGER NOT NULL,
            routeColor TEXT DEFAULT 'FFFFFF',
            routeTextColor TEXT DEFAULT '000000'
        );

        CREATE TABLE trips (
            tripId TEXT PRIMARY KEY,
            routeId TEXT NOT NULL,
            serviceId TEXT NOT NULL,
            tripHeadsign TEXT DEFAULT '',
            directionId INTEGER DEFAULT 0,
            shapeId TEXT DEFAULT ''
        );

        CREATE TABLE stop_times (
            tripId TEXT NOT NULL,
            stopId TEXT NOT NULL,
            stopSequence INTEGER NOT NULL,
            arrivalTime TEXT NOT NULL,
            departureTime TEXT NOT NULL,
            PRIMARY KEY (tripId, stopSequence)
        );

        -- NEW: Calendar table for day-of-week filtering
        CREATE TABLE calendar (
            serviceId TEXT PRIMARY KEY,
            monday INTEGER,
            tuesday INTEGER,
            wednesday INTEGER,
            thursday INTEGER,
            friday INTEGER,
            saturday INTEGER,
            sunday INTEGER,
            startDate TEXT,
            endDate TEXT
        );

        CREATE TABLE shapes (
            shapeId TEXT NOT NULL,
            lat REAL NOT NULL,
            lng REAL NOT NULL,
            sequence INTEGER NOT NULL,
            PRIMARY KEY (shapeId, sequence)
        );
    """)

    with zipfile.ZipFile(io.BytesIO(gtfs_bytes)) as zf:
        # Parsing logic...
        cursor.executemany("INSERT OR REPLACE INTO stops VALUES (?, ?, ?, ?, ?)", 
            ((r["stop_id"], r.get("stop_name", ""), float(r["stop_lat"]), float(r["stop_lon"]), int(r.get("wheelchair_boarding") or 0)) 
             for r in iter_csv_from_zip(zf, "stops.txt")))

        cursor.executemany("INSERT OR REPLACE INTO routes VALUES (?, ?, ?, ?, ?, ?)", 
            ((r["route_id"], r.get("route_short_name", ""), r.get("route_long_name", ""), int(r.get("route_type") or 3), r.get("route_color", "FFFFFF"), r.get("route_text_color", "000000")) 
             for r in iter_csv_from_zip(zf, "routes.txt")))

        cursor.executemany("INSERT OR REPLACE INTO trips VALUES (?, ?, ?, ?, ?, ?)", 
            ((r["trip_id"], r["route_id"], r["service_id"], r.get("trip_headsign", ""), int(r.get("direction_id") or 0), r.get("shape_id", "")) 
             for r in iter_csv_from_zip(zf, "trips.txt")))

        cursor.executemany("INSERT OR REPLACE INTO stop_times VALUES (?, ?, ?, ?, ?)", 
            ((r["trip_id"], r["stop_id"], int(r["stop_sequence"]), r["arrival_time"], r["departure_time"]) 
             for r in iter_csv_from_zip(zf, "stop_times.txt")))

        # NEW: Calendar parsing
        print("Parsing calendar...")
        cursor.executemany("INSERT OR REPLACE INTO calendar VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", 
            ((r["service_id"], int(r["monday"]), int(r["tuesday"]), int(r["wednesday"]), int(r["thursday"]), 
              int(r["friday"]), int(r["saturday"]), int(r["sunday"]), r["start_date"], r["end_date"]) 
             for r in iter_csv_from_zip(zf, "calendar.txt")))

        cursor.executemany("INSERT OR REPLACE INTO shapes VALUES (?, ?, ?, ?)", 
            ((r["shape_id"], float(r["shape_pt_lat"]), float(r["shape_pt_lon"]), int(r["shape_pt_sequence"])) 
             for r in iter_csv_from_zip(zf, "shapes.txt")))

    print("Generating indexes...")
    cursor.executescript("""
        CREATE INDEX idx_stoptimes_stop_id ON stop_times(stopId);
        CREATE INDEX idx_trips_route_id ON trips(routeId);
        CREATE INDEX idx_stoptimes_trip_id ON stop_times(tripId);
        CREATE INDEX idx_stops_coords ON stops(lat, lng);
        CREATE INDEX idx_calendar_service ON calendar(serviceId);
    """)

    conn.commit()
    cursor.execute("VACUUM")
    conn.close()
    print(f"DB generated: {OUTPUT_DB}")

def main():
    try:
        build_db(download_gtfs())
        print("SUCCESS")
    except Exception as e:
        print(f"FAILED: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()
    
