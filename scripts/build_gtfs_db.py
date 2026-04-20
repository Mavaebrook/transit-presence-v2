#!/usr/bin/env python3
"""
build_gtfs_db.py
Downloads LYNX GTFS feed and generates a compiled SQLite DB
for direct use in Android (vanilla SQLite, no Room).
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
            req = urllib.request.Request(
                url,
                headers={"User-Agent": "TransitPresence/1.0"}
            )
            with urllib.request.urlopen(req, timeout=60) as response:
                data = response.read()

                if len(data) > 10000 and data[:2] == b"PK":
                    print(f"Downloaded {len(data):,} bytes from {url}")
                    return data
                else:
                    print(f"Invalid ZIP or HTML response from {url}")

        except Exception as e:
            print(f"Failed {url}: {e}")

    raise Exception("All GTFS download URLs failed")


# ---------------- PARSE ZIP CSV (GENERATOR) ----------------

def iter_csv_from_zip(zf, filename):
    """Yields rows one by one to save RAM on massive GTFS files"""
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

    print(f"Creating database at {OUTPUT_DB}...")
    conn = sqlite3.connect(OUTPUT_DB)
    cursor = conn.cursor()

    # ---------------- SCHEMA ----------------
    cursor.executescript("""
        PRAGMA synchronous = OFF;
        PRAGMA journal_mode = MEMORY;

        CREATE TABLE IF NOT EXISTS stops (
            stopId TEXT PRIMARY KEY,
            stopName TEXT NOT NULL,
            lat REAL NOT NULL,
            lng REAL NOT NULL,
            wheelchairBoarding INTEGER DEFAULT 0
        );

        CREATE TABLE IF NOT EXISTS routes (
            routeId TEXT PRIMARY KEY,
            routeShortName TEXT NOT NULL,
            routeLongName TEXT NOT NULL,
            routeType INTEGER NOT NULL,
            routeColor TEXT DEFAULT 'FFFFFF',
            routeTextColor TEXT DEFAULT '000000'
        );

        CREATE TABLE IF NOT EXISTS trips (
            tripId TEXT PRIMARY KEY,
            routeId TEXT NOT NULL,
            serviceId TEXT NOT NULL,
            tripHeadsign TEXT DEFAULT '',
            directionId INTEGER DEFAULT 0,
            shapeId TEXT DEFAULT ''
        );

        CREATE TABLE IF NOT EXISTS stop_times (
            tripId TEXT NOT NULL,
            stopId TEXT NOT NULL,
            stopSequence INTEGER NOT NULL,
            arrivalTime TEXT NOT NULL,
            departureTime TEXT NOT NULL,
            PRIMARY KEY (tripId, stopSequence)
        );

        CREATE TABLE IF NOT EXISTS shapes (
            shapeId TEXT NOT NULL,
            lat REAL NOT NULL,
            lng REAL NOT NULL,
            sequence INTEGER NOT NULL,
            PRIMARY KEY (shapeId, sequence)
        );
    """)

    # ---------------- PROCESS ZIP ----------------
    with zipfile.ZipFile(io.BytesIO(gtfs_bytes)) as zf:
        print("ZIP contains:", zf.namelist())

        # ---------------- STOPS ----------------
        print("Parsing stops...")
        def get_stops():
            for r in iter_csv_from_zip(zf, "stops.txt"):
                try:
                    yield (
                        r["stop_id"],
                        r.get("stop_name", ""),
                        float(r["stop_lat"]),
                        float(r["stop_lon"]),
                        int(r.get("wheelchair_boarding") or 0)
                    )
                except Exception:
                    pass
        cursor.executemany("INSERT OR REPLACE INTO stops VALUES (?, ?, ?, ?, ?)", get_stops())

        # ---------------- ROUTES ----------------
        print("Parsing routes...")
        def get_routes():
            for r in iter_csv_from_zip(zf, "routes.txt"):
                try:
                    yield (
                        r["route_id"],
                        r.get("route_short_name", ""),
                        r.get("route_long_name", ""),
                        int(r.get("route_type") or 3),
                        r.get("route_color", "FFFFFF"),
                        r.get("route_text_color", "000000")
                    )
                except Exception:
                    pass
        cursor.executemany("INSERT OR REPLACE INTO routes VALUES (?, ?, ?, ?, ?, ?)", get_routes())

        # ---------------- TRIPS ----------------
        print("Parsing trips...")
        def get_trips():
            for r in iter_csv_from_zip(zf, "trips.txt"):
                try:
                    yield (
                        r["trip_id"],
                        r["route_id"],
                        r.get("service_id", ""),
                        r.get("trip_headsign", ""),
                        int(r.get("direction_id") or 0),
                        r.get("shape_id", "")
                    )
                except Exception:
                    pass
        cursor.executemany("INSERT OR REPLACE INTO trips VALUES (?, ?, ?, ?, ?, ?)", get_trips())

        # ---------------- STOP TIMES ----------------
        print("Parsing stop times (this may take a moment)...")
        def get_stop_times():
            for r in iter_csv_from_zip(zf, "stop_times.txt"):
                try:
                    yield (
                        r["trip_id"],
                        r["stop_id"],
                        int(r["stop_sequence"]),
                        r.get("arrival_time", ""),
                        r.get("departure_time", "")
                    )
                except Exception:
                    pass
        cursor.executemany("INSERT OR REPLACE INTO stop_times VALUES (?, ?, ?, ?, ?)", get_stop_times())

        # ---------------- SHAPES ----------------
        print("Parsing shapes...")
        def get_shapes():
            for r in iter_csv_from_zip(zf, "shapes.txt"):
                try:
                    yield (
                        r["shape_id"],
                        float(r["shape_pt_lat"]),
                        float(r["shape_pt_lon"]),
                        int(r["shape_pt_sequence"])
                    )
                except Exception:
                    pass
        cursor.executemany("INSERT OR REPLACE INTO shapes VALUES (?, ?, ?, ?)", get_shapes())

    # ---------------- FINALIZE ----------------
    conn.commit()
    print("Optimizing database...")
    cursor.execute("VACUUM") # Compress the DB file size
    conn.close()

    size = os.path.getsize(OUTPUT_DB)
    print(f"\nDB generated: {OUTPUT_DB} ({size / 1024 / 1024:.2f} MB)")


# ---------------- MAIN ----------------

def main():
    try:
        gtfs_bytes = download_gtfs()
        build_db(gtfs_bytes)
        print("SUCCESS")
    except Exception as e:
        print(f"FAILED: {e}")
        sys.exit(1)


if __name__ == "__main__":
    main()
    
