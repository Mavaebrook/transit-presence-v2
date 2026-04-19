#!/usr/bin/env python3
"""
build_gtfs_db.py
Downloads the LYNX GTFS ZIP and builds a pre-populated SQLite database
that ships inside the app as an asset.
"""

import sqlite3
import zipfile
import urllib.request
import csv
import io
import os
import sys

GTFS_URL = "https://www.goLynx.com/plan-your-trip/google-transit.stml"
FALLBACK_URL = "http://gtfsrt.golynx.com/gtfsrt/google_transit.zip"
OUTPUT_DB = "app/src/main/assets/transit_prepopulated.db"

def download_gtfs():
    print("Downloading GTFS static feed...")
    for url in [GTFS_URL, FALLBACK_URL]:
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "TransitPresence/1.0"})
            with urllib.request.urlopen(req, timeout=60) as response:
                data = response.read()
                if len(data) > 10000:
                    print(f"Downloaded {len(data)} bytes from {url}")
                    return data
        except Exception as e:
            print(f"Failed {url}: {e}")
    raise Exception("All GTFS download URLs failed")

def create_schema(conn):
    conn.executescript("""
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
        CREATE TABLE IF NOT EXISTS room_master_table (
            id INTEGER PRIMARY KEY,
            identity_hash TEXT
        );
        INSERT OR REPLACE INTO room_master_table VALUES (42, 'transit_presence_v1');
    """)
    conn.commit()

def parse_csv_from_zip(zf, filename):
    if filename not in zf.namelist():
        print(f"  Skipping {filename} — not found in ZIP")
        return []
    with zf.open(filename) as f:
        content = f.read().decode("utf-8-sig")
        reader = csv.DictReader(io.StringIO(content))
        return list(reader)

def build_database(gtfs_bytes):
    os.makedirs("app/src/main/assets", exist_ok=True)

    if os.path.exists(OUTPUT_DB):
        os.remove(OUTPUT_DB)

    conn = sqlite3.connect(OUTPUT_DB)
    create_schema(conn)

    with zipfile.ZipFile(io.BytesIO(gtfs_bytes)) as zf:
        print(f"ZIP contains: {zf.namelist()}")

        # Stops
        rows = parse_csv_from_zip(zf, "stops.txt")
        stops = []
        for row in rows:
            try:
                stops.append((
                    row["stop_id"].strip(),
                    row.get("stop_name", "").strip(),
                    float(row["stop_lat"]),
                    float(row["stop_lon"]),
                    int(row.get("wheelchair_boarding", 0) or 0),
                ))
            except (ValueError, KeyError):
                continue
        conn.executemany(
            "INSERT OR REPLACE INTO stops VALUES (?,?,?,?,?)", stops
        )
        conn.commit()
        print(f"  Inserted {len(stops)} stops")

        # Routes
        rows = parse_csv_from_zip(zf, "routes.txt")
        routes = []
        for row in rows:
            try:
                routes.append((
                    row["route_id"].strip(),
                    row.get("route_short_name", "").strip(),
                    row.get("route_long_name", "").strip(),
                    int(row.get("route_type", 3) or 3),
                    row.get("route_color", "FFFFFF").strip() or "FFFFFF",
                    row.get("route_text_color", "000000").strip() or "000000",
                ))
            except (ValueError, KeyError):
                continue
        conn.executemany(
            "INSERT OR REPLACE INTO routes VALUES (?,?,?,?,?,?)", routes
        )
        conn.commit()
        print(f"  Inserted {len(routes)} routes")

        # Trips
        rows = parse_csv_from_zip(zf, "trips.txt")
        trips = []
        for row in rows:
            try:
                trips.append((
                    row["trip_id"].strip(),
                    row["route_id"].strip(),
                    row.get("service_id", "").strip(),
                    row.get("trip_headsign", "").strip(),
                    int(row.get("direction_id", 0) or 0),
                    row.get("shape_id", "").strip(),
                ))
            except (ValueError, KeyError):
                continue
        conn.executemany(
            "INSERT OR REPLACE INTO trips VALUES (?,?,?,?,?,?)", trips
        )
        conn.commit()
        print(f"  Inserted {len(trips)} trips")

        # Stop times — batch insert
        rows = parse_csv_from_zip(zf, "stop_times.txt")
        batch = []
        count = 0
        for row in rows:
            try:
                batch.append((
                    row["trip_id"].strip(),
                    row["stop_id"].strip(),
                    int(row["stop_sequence"]),
                    row.get("arrival_time", "").strip(),
                    row.get("departure_time", "").strip(),
                ))
                if len(batch) >= 10000:
                    conn.executemany(
                        "INSERT OR REPLACE INTO stop_times VALUES (?,?,?,?,?)",
                        batch
                    )
                    conn.commit()
                    count += len(batch)
                    batch = []
            except (ValueError, KeyError):
                continue
        if batch:
            conn.executemany(
                "INSERT OR REPLACE INTO stop_times VALUES (?,?,?,?,?)", batch
            )
            conn.commit()
            count += len(batch)
        print(f"  Inserted {count} stop times")

        # Shapes — batch insert
        rows = parse_csv_from_zip(zf, "shapes.txt")
        batch = []
        count = 0
        for row in rows:
            try:
                batch.append((
                    row["shape_id"].strip(),
                    float(row["shape_pt_lat"]),
                    float(row["shape_pt_lon"]),
                    int(row["shape_pt_sequence"]),
                ))
                if len(batch) >= 10000:
                    conn.executemany(
                        "INSERT OR REPLACE INTO shapes VALUES (?,?,?,?)",
                        batch
                    )
                    conn.commit()
                    count += len(batch)
                    batch = []
            except (ValueError, KeyError):
                continue
        if batch:
            conn.executemany(
                "INSERT OR REPLACE INTO shapes VALUES (?,?,?,?)", batch
            )
            conn.commit()
            count += len(batch)
        print(f"  Inserted {count} shape points")

    conn.execute("VACUUM")
    conn.close()

    size = os.path.getsize(OUTPUT_DB)
    print(f"\nDatabase built: {OUTPUT_DB} ({size:,} bytes)")

if __name__ == "__main__":
    try:
        gtfs_bytes = download_gtfs()
        build_database(gtfs_bytes)
        print("SUCCESS")
    except Exception as e:
        print(f"FAILED: {e}")
        sys.exit(1)
