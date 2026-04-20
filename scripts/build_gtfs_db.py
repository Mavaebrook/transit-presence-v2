#!/usr/bin/env python3
"""
build_gtfs_sql.py
Downloads LYNX GTFS feed and generates a plain SQL dump
for direct execution in Android (no Room, no prebuilt SQLite DB).
"""

import zipfile
import urllib.request
import csv
import io
import os
import sys

# ---------------- GTFS SOURCES ----------------
GTFS_URL = "http://gtfsrt.golynx.com/gtfsrt/google_transit.zip"
FALLBACK_URL = "http://www.golynx.com/lynxmap/GoLYNX_data/google_transit.zip"

OUTPUT_SQL = "app/src/main/assets/transit_prepopulated.sql"


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
                    print(f"Downloaded {len(data)} bytes from {url}")
                    return data
                else:
                    print(f"Invalid ZIP or HTML response from {url}")

        except Exception as e:
            print(f"Failed {url}: {e}")

    raise Exception("All GTFS download URLs failed")


# ---------------- PARSE ZIP CSV ----------------

def parse_csv_from_zip(zf, filename):
    if filename not in zf.namelist():
        print(f"Skipping {filename}")
        return []

    with zf.open(filename) as f:
        content = f.read().decode("utf-8-sig")
        reader = csv.DictReader(io.StringIO(content))
        return list(reader)


# ---------------- SQL ESCAPE ----------------

def escape_sql(value):
    if value is None:
        return ""
    return str(value).replace("'", "''")


# ---------------- BUILD SQL ----------------

def build_sql(gtfs_bytes):
    os.makedirs("app/src/main/assets", exist_ok=True)

    sql = []

    # ---------------- SCHEMA ----------------
    sql.append("""
CREATE TABLE IF NOT EXISTS stops (
    stopId TEXT PRIMARY KEY,
    stopName TEXT NOT NULL,
    lat REAL NOT NULL,
    lng REAL NOT NULL,
    wheelchairBoarding INTEGER DEFAULT 0
);
""")

    sql.append("""
CREATE TABLE IF NOT EXISTS routes (
    routeId TEXT PRIMARY KEY,
    routeShortName TEXT NOT NULL,
    routeLongName TEXT NOT NULL,
    routeType INTEGER NOT NULL,
    routeColor TEXT DEFAULT 'FFFFFF',
    routeTextColor TEXT DEFAULT '000000'
);
""")

    sql.append("""
CREATE TABLE IF NOT EXISTS trips (
    tripId TEXT PRIMARY KEY,
    routeId TEXT NOT NULL,
    serviceId TEXT NOT NULL,
    tripHeadsign TEXT DEFAULT '',
    directionId INTEGER DEFAULT 0,
    shapeId TEXT DEFAULT ''
);
""")

    sql.append("""
CREATE TABLE IF NOT EXISTS stop_times (
    tripId TEXT NOT NULL,
    stopId TEXT NOT NULL,
    stopSequence INTEGER NOT NULL,
    arrivalTime TEXT NOT NULL,
    departureTime TEXT NOT NULL,
    PRIMARY KEY (tripId, stopSequence)
);
""")

    sql.append("""
CREATE TABLE IF NOT EXISTS shapes (
    shapeId TEXT NOT NULL,
    lat REAL NOT NULL,
    lng REAL NOT NULL,
    sequence INTEGER NOT NULL,
    PRIMARY KEY (shapeId, sequence)
);
""")

    sql.append("""
INSERT OR REPLACE INTO room_master_table VALUES (42, 'transit_presence_v1');
""")

    # ---------------- PROCESS ZIP ----------------
    with zipfile.ZipFile(io.BytesIO(gtfs_bytes)) as zf:
        print("ZIP contains:", zf.namelist())

        # ---------------- STOPS ----------------
        for r in parse_csv_from_zip(zf, "stops.txt"):
            try:
                sql.append(f"""
INSERT OR REPLACE INTO stops VALUES (
'{escape_sql(r["stop_id"])}',
'{escape_sql(r.get("stop_name", ""))}',
{float(r["stop_lat"])},
{float(r["stop_lon"])},
{int(r.get("wheelchair_boarding", 0) or 0)}
);
""")
            except Exception:
                continue

        # ---------------- ROUTES ----------------
        for r in parse_csv_from_zip(zf, "routes.txt"):
            try:
                sql.append(f"""
INSERT OR REPLACE INTO routes VALUES (
'{escape_sql(r["route_id"])}',
'{escape_sql(r.get("route_short_name", ""))}',
'{escape_sql(r.get("route_long_name", ""))}',
{int(r.get("route_type", 3) or 3)},
'{escape_sql(r.get("route_color", "FFFFFF"))}',
'{escape_sql(r.get("route_text_color", "000000"))}'
);
""")
            except Exception:
                continue

        # ---------------- TRIPS ----------------
        for r in parse_csv_from_zip(zf, "trips.txt"):
            try:
                sql.append(f"""
INSERT OR REPLACE INTO trips VALUES (
'{escape_sql(r["trip_id"])}',
'{escape_sql(r["route_id"])}',
'{escape_sql(r.get("service_id", ""))}',
'{escape_sql(r.get("trip_headsign", ""))}',
{int(r.get("direction_id", 0) or 0)},
'{escape_sql(r.get("shape_id", ""))}'
);
""")
            except Exception:
                continue

        # ---------------- STOP TIMES ----------------
        for r in parse_csv_from_zip(zf, "stop_times.txt"):
            try:
                sql.append(f"""
INSERT OR REPLACE INTO stop_times VALUES (
'{escape_sql(r["trip_id"])}',
'{escape_sql(r["stop_id"])}',
{int(r["stop_sequence"])},
'{escape_sql(r.get("arrival_time", ""))}',
'{escape_sql(r.get("departure_time", ""))}'
);
""")
            except Exception:
                continue

        # ---------------- SHAPES ----------------
        for r in parse_csv_from_zip(zf, "shapes.txt"):
            try:
                sql.append(f"""
INSERT OR REPLACE INTO shapes VALUES (
'{escape_sql(r["shape_id"])}',
{float(r["shape_pt_lat"])},
{float(r["shape_pt_lon"])},
{int(r["shape_pt_sequence"])}
);
""")
            except Exception:
                continue

    # ---------------- WRITE FILE ----------------
    with open(OUTPUT_SQL, "w", encoding="utf-8") as f:
        f.write("\n".join(sql))

    size = os.path.getsize(OUTPUT_SQL)
    print(f"\nSQL generated: {OUTPUT_SQL} ({size:,} bytes)")


# ---------------- MAIN ----------------

def main():
    try:
        gtfs_bytes = download_gtfs()
        build_sql(gtfs_bytes)
        print("SUCCESS")
    except Exception as e:
        print(f"FAILED: {e}")
        sys.exit(1)


if __name__ == "__main__":
    main()
